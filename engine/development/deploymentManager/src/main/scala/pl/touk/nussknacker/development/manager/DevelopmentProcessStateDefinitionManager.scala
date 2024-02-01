package pl.touk.nussknacker.development.manager

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{
  ActionName,
  OverridingProcessStateDefinitionManager,
  ProcessActionType,
  ProcessStateDefinitionManager,
  StateDefinitionDetails,
  StateStatus
}

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

  val AfterRunningActionName: ActionName       = ActionName("AFTER")
  val PreparingResourcesActionName: ActionName = ActionName("PREPARING")
  val TestActionName: ActionName               = ActionName("TEST")

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case DevelopmentStateStatus.AfterRunningStatus       => List(ProcessActionType.Cancel)
    case DevelopmentStateStatus.PreparingResourcesStatus => List(ProcessActionType.Deploy)
    case DevelopmentStateStatus.TestStatus               => List(ProcessActionType.Deploy)
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
