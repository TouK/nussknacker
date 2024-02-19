package pl.touk.nussknacker.ui.factory

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ProcessingTypeData,
  ProcessingTypeDataState
}

trait ProcessingTypeDataStateFactory {

  def create(
      designerConfig: ConfigWithUnresolvedVersion,
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]

}
