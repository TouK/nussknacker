package pl.touk.nussknacker.development.manager

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._

class DevelopmentProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager)
    extends OverridingProcessStateDefinitionManager(
      statusActionsPF = DevelopmentStateStatus.statusActionsPF,
      customStateDefinitions = DevelopmentStateStatus.customStateDefinitions,
      delegate = delegate
    )

object DevelopmentStateStatus {

  val AfterRunningStatus: StateStatus       = StateStatus("AFTER")
  val PreparingResourcesStatus: StateStatus = StateStatus("PREPARING")
  val TestStatus: StateStatus               = StateStatus("TEST")

  val AfterRunningActionName: ScenarioActionName       = ScenarioActionName("AFTER")
  val PreparingResourcesActionName: ScenarioActionName = ScenarioActionName("PREPARING")
  val TestActionName: ScenarioActionName               = ScenarioActionName("TEST")

  val statusActionsPF: PartialFunction[ProcessStatus, List[ScenarioActionName]] = {
    case ProcessStatus(DevelopmentStateStatus.AfterRunningStatus, _, _)       => List(ScenarioActionName.Cancel)
    case ProcessStatus(DevelopmentStateStatus.PreparingResourcesStatus, _, _) => List(ScenarioActionName.Deploy)
    case ProcessStatus(DevelopmentStateStatus.TestStatus, _, _)               => List(ScenarioActionName.Deploy)
  }

  val customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
    AfterRunningStatus.name -> StateDefinitionDetails(
      displayableName = "After running",
      icon = UnknownIcon,
      tooltip = "External running.",
      description = "External running."
    ),
    PreparingResourcesStatus.name -> StateDefinitionDetails(
      displayableName = "Preparing resources",
      icon = UnknownIcon,
      tooltip = "Preparing external resources.",
      description = "Preparing external resources."
    ),
    TestStatus.name -> StateDefinitionDetails(
      displayableName = "Test",
      icon = UnknownIcon,
      tooltip = "Preparing external resources.",
      description = "Preparing external resources."
    ),
  )

}
