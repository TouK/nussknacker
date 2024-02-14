package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StateDefinitionDeduplicationResult

// All information in combined data is kept for sake of clarity that this state is verified
// across all processing types and is created and verified once after all processing types reload
final case class CombinedProcessingTypeData(
    statusNameToStateDefinitionsMapping: Map[StatusName, StateDefinitionDeduplicationResult],
    parametersService: ScenarioParametersService,
)

object CombinedProcessingTypeData {

  def create(
      processingTypes: Map[ProcessingType, ProcessingTypeData]
  ): CombinedProcessingTypeData = {
    val parametersService =
      ScenarioParametersService.createUnsafe(processingTypes.mapValuesNow(_.scenarioParameters))
    CombinedProcessingTypeData(
      statusNameToStateDefinitionsMapping =
        ProcessStateDefinitionService.createDefinitionsMappingUnsafe(processingTypes),
      parametersService = parametersService
    )
  }

}
