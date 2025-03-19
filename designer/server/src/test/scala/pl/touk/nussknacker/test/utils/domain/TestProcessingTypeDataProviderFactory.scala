package pl.touk.nussknacker.test.utils.domain

import cats.effect.unsafe.implicits.global
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelDependencies}
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
      modelDependencies: ModelDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      deploymentManagerDependencies: DeploymentManagerDependencies,
  ): ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData] = {
    val finalProcessingTypeData =
      loadModelData(processingTypeConfigs, modelClassLoaderProvider, modelDependencies)
        .transform { case (modelDataWithInputs, _) =>
          val (deploymentData, _) = loadDeploymentManagers(
            processingTypeConfigs,
            modelClassLoaderProvider,
            deploymentManagersClassLoader,
            deploymentManagerDependencies,
            modelDataWithInputs
          ).allocated
            .unsafeRunSync()
          createProcessingTypeDataState(modelDataWithInputs, deploymentData)
        }

    ProcessingTypeDataProvider.fromState(finalProcessingTypeData)
  }

  private def loadModelData(
      processingTypeConfigs: ProcessingTypeConfigs,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      modelDependencies: ModelDependencies
  ) = {
    ModelDataLoader
      .load(
        processingTypeConfigs,
        _ => modelDependencies,
        modelClassLoaderProvider
      )
  }

  private def loadDeploymentManagers(
      processingTypeConfigs: ProcessingTypeConfigs,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      modelDataWithInputs: Map[ProcessingType, ValueWithRestriction[
        ProcessingTypeDataStateFactory.ModelDataWithProcessingTypeDataInput
      ]]
  ) = {
    DeploymentManagersLoader
      .load(
        processingTypeConfigs,
        deploymentManagersClassLoader,
        modelClassLoaderProvider,
        createWithEmptyCombinedData(modelDataWithInputs).mapValues(_.modelData),
        _ => deploymentManagerDependencies,
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
