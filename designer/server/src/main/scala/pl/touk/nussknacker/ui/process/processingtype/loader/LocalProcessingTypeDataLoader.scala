package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ModelClassLoaderProvider,
  ProcessingTypeData
}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData.SchedulingForProcessingType
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataLoader.toValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState

class LocalProcessingTypeDataLoader(
    modelData: Map[ProcessingType, (String, ModelData)],
    deploymentManagerProvider: DeploymentManagerProvider
) extends ProcessingTypeDataLoader {

  override def loadProcessingTypeData(
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      dbRef: Option[DbRef],
  ): IO[ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]] = IO {
    val processingTypes = modelData.map { case (processingType, (category, model)) =>
      val deploymentManagerDependencies = getDeploymentManagerDependencies(processingType)
      val data = ProcessingTypeData.createProcessingTypeData(
        name = processingType,
        modelData = model,
        deploymentManagerProvider = deploymentManagerProvider,
        schedulingForProcessingType = SchedulingForProcessingType.NotAvailable,
        deploymentManagerDependencies = deploymentManagerDependencies,
        engineSetupName = deploymentManagerProvider.defaultEngineSetupName,
        deploymentConfig = ConfigFactory.empty(),
        category = category,
        componentDefinitionExtractionMode = getModelDependencies(processingType).componentDefinitionExtractionMode,
      )
      processingType -> data
    }

    val combinedData = CombinedProcessingTypeData.create(processingTypes)
    ProcessingTypeDataState(processingTypes.mapValuesNow(toValueWithRestriction), () => combinedData, new Object)
  }

}
