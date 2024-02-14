package pl.touk.nussknacker.ui.factory

import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, DeploymentManagerDependencies}
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ProcessingTypeData,
  ProcessingTypeDataState
}

import java.nio.file.Path

trait ProcessingTypeDataStateFactory {

  def create(
      designerConfig: ConfigWithUnresolvedVersion,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      workingDirectoryOpt: Option[Path]
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]

}
