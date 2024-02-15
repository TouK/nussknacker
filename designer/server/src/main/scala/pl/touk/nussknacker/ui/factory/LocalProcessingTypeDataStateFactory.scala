package pl.touk.nussknacker.ui.factory

import com.typesafe.config.Config
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataReader.toValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ProcessingTypeData,
  ProcessingTypeDataState
}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel.{category, typeName}

import java.nio.file.Path

class LocalProcessingTypeDataStateFactory(
    modelData: ModelData,
    deploymentManagerProvider: DeploymentManagerProvider,
    managerConfig: Config
) extends ProcessingTypeDataStateFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      workingDirectoryOpt: Option[Path],
      skipComponentProvidersLoadedFromAppClassloader: Boolean
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    val deploymentManagerDependencies = getDeploymentManagerDependencies(typeName)
    val data =
      ProcessingTypeData.createProcessingTypeData(
        typeName,
        deploymentManagerProvider,
        deploymentManagerDependencies,
        deploymentManagerProvider.defaultEngineSetupName,
        modelData,
        managerConfig,
        category,
      )
    val processingTypes = Map(typeName -> data)
    val combinedData    = CombinedProcessingTypeData.create(processingTypes)
    ProcessingTypeDataState(processingTypes.mapValuesNow(toValueWithRestriction), () => combinedData, new Object)
  }

}
