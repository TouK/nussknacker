package pl.touk.nussknacker.test.utils.domain

import cats.effect.IO
import cats.effect.kernel.Resource
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelData, ModelDependencies}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.classloader.DeploymentManagersClassLoader
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.loader.{
  DeploymentManagersLoader,
  ModelDataLoader,
  ProcessingTypeDataStateFactory
}
import pl.touk.nussknacker.ui.process.processingtype.provider.{ProcessingTypeDataProvider, ProcessingTypeDataState}

import scala.util.{Failure, Success}

object TestProcessingTypeDataProviderFactory {

  def create[T, C](
      allValues: Map[ProcessingType, ValueWithRestriction[T]],
      combinedValue: C
  ): ProcessingTypeDataProvider[T, C] =
    ProcessingTypeDataProvider.fromState(
      new ProcessingTypeDataState(
        allValues,
        Success(combinedValue),
      )
    )

  def createWithEmptyCombinedData[T](
      allValues: Map[ProcessingType, ValueWithRestriction[T]]
  ): ProcessingTypeDataProvider[T, Nothing] =
    ProcessingTypeDataProvider.fromState(
      new ProcessingTypeDataState(
        allValues,
        Failure(
          new IllegalStateException(
            "Processing type data provider does not have combined data!"
          )
        ),
      )
    )

  def create(
      processingTypeConfigs: ProcessingTypeConfigs,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      modelConfig: ModelDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      deploymentManagerDependencies: DeploymentManagerDependencies,
  ): Resource[IO, ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData]] = {

    val modelDataProvider = loadModelData(processingTypeConfigs, modelClassLoaderProvider, modelConfig)
    for {
      deploymentDatas <- loadDeploymentManagers(
        processingTypeConfigs,
        modelClassLoaderProvider,
        deploymentManagersClassLoader,
        deploymentManagerDependencies,
        modelDataProvider.mapValues(_.modelData)
      )
      processingTypeDataProvider = modelDataProvider.transform { case (modelDataWithInputs, _) =>
        createProcessingTypeDataState(modelDataWithInputs, deploymentDatas)

      }
    } yield processingTypeDataProvider
  }

  private def loadModelData(
      processingTypeConfigs: ProcessingTypeConfigs,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      modelConfig: ModelDependencies
  ) = {
    ProcessingTypeDataProvider
      .fromState(
        ModelDataLoader
          .load(
            processingTypeConfigs,
            _ => modelConfig,
            modelClassLoaderProvider
          )
      )
  }

  private def loadDeploymentManagers(
      processingTypeConfigs: ProcessingTypeConfigs,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      modelDataProvider: ProcessingTypeDataProvider[ModelData, _]
  ) = {
    DeploymentManagersLoader
      .load(
        processingTypeConfigs,
        deploymentManagersClassLoader,
        modelClassLoaderProvider,
        modelDataProvider,
        deploymentManagerDependencies,
        schedulingDepsProvider = None
      )
  }

  private def createProcessingTypeDataState(
      modelDataWithInputs: Map[ProcessingType, ValueWithRestriction[
        ProcessingTypeDataStateFactory.ModelDataWithProcessingTypeDataInput
      ]],
      deploymentData: Map[ProcessingType, ValueWithRestriction[DeploymentData]]
  ) = {
    ProcessingTypeDataStateFactory
      .create(
        modelDataWithInputs,
        deploymentData
      )
      .toEither
      .toTry
      .get
  }

}
