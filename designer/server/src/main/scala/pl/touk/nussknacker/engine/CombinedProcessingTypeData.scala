package pl.touk.nussknacker.engine

import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StatusNameToStateDefinitionsMapping

case class CombinedProcessingTypeData(statusNameToStateDefinitionsMapping: StatusNameToStateDefinitionsMapping,
                                     )

object CombinedProcessingTypeData {

  def create(processingTypes: Map[ProcessingType, ProcessingTypeData]): CombinedProcessingTypeData = {
    CombinedProcessingTypeData(
      statusNameToStateDefinitionsMapping = ProcessStateDefinitionService.createDefinitionsMappingUnsafe(processingTypes),
    )
  }

}
