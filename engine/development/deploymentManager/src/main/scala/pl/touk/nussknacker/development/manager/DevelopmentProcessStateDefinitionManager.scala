package pl.touk.nussknacker.development.manager

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType, ProcessStateDefinitionManager, StateDefinitionDetails, StateStatus}

class DevelopmentProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager) extends OverridingProcessStateDefinitionManager(
  statusActionsPF = DevelopmentStateStatus.statusActionsPF,
  customStateDefinitions = DevelopmentStateStatus.customStateDefinitions,
  delegate = delegate
)

object DevelopmentStateStatus {

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case DevelopmentStateStatus.AfterRunningStatus => List(ProcessActionType.Cancel)
    case DevelopmentStateStatus.PreparingResourcesStatus => List(ProcessActionType.Deploy)
    case DevelopmentStateStatus.TestStatus => List(ProcessActionType.Deploy)
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

  case object AfterRunningStatus extends StateStatus {
    override def name: StatusName = "AFTER"
    override def isRunning: Boolean = true
  }

  case object PreparingResourcesStatus extends StateStatus {
    override def name: StatusName = "PREPARING"
  }

  case object TestStatus extends StateStatus {
    override def name: StatusName = "TEST"
  }

}
