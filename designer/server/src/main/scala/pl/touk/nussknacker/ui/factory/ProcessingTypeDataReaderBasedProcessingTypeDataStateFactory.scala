package pl.touk.nussknacker.ui.factory

import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, DeploymentManagerDependencies}
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ProcessingTypeData,
  ProcessingTypeDataReader,
  ProcessingTypeDataState
}

import java.nio.file.Path

object ProcessingTypeDataReaderBasedProcessingTypeDataStateFactory extends ProcessingTypeDataStateFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      workingDirectoryOpt: Option[Path]
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    ProcessingTypeDataReader.loadProcessingTypeData(
      designerConfig,
      getDeploymentManagerDependencies,
      additionalUIConfigProvider,
      workingDirectoryOpt
    )
  }

}
