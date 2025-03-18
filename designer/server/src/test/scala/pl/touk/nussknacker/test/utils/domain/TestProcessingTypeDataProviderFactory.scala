package pl.touk.nussknacker.test.utils.domain

import cats.effect.unsafe.implicits.global
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.SchedulingDependencies
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ModelClassLoaderProvider,
  ProcessingTypeData,
  ValueWithRestriction
}
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
      ModelDataLoader
        .load(
          processingTypeConfigs,
          _ => modelDependencies,
          modelClassLoaderProvider
        )
        .transform { case (modelDataWithInputs, _) =>
          val deploymentData = DeploymentManagersLoader
            .load(
              processingTypeConfigs,
              deploymentManagersClassLoader,
              modelClassLoaderProvider,
              createWithEmptyCombinedData(modelDataWithInputs).mapValues(_.modelData),
              _ => deploymentManagerDependencies,
              schedulingDepsProvider = None
            )
            .allocated
            .unsafeRunSync()
            ._1
          ProcessingTypeDataStateFactory
            .create(
              modelDataWithInputs,
              deploymentData
            )
            .toEither
            .toTry
            .get
        }

    ProcessingTypeDataProvider.fromState(finalProcessingTypeData)
  }

}
