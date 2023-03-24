package pl.touk.nussknacker.development.manager

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.unknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{CustomStateStatus, OverridingProcessStateDefinitionManager, ProcessActionType, ProcessStateDefinitionManager, StateDefinitionDetails, StateStatus}

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
      icon = unknownIcon,
      tooltip = Some("External running."),
      description = Some("External running.")
    ),
    PreparingResourcesStatus.name -> StateDefinitionDetails(
      displayableName = "Preparing resources",
      icon = unknownIcon,
      tooltip = Some("Preparing external resources."),
      description = Some("Preparing external resources.")
    ),
    TestStatus.name -> StateDefinitionDetails(
      displayableName = "Test",
      icon = unknownIcon,
      tooltip = Some("Preparing external resources."),
      description = Some("Preparing external resources.")
    ),
  )

  case object AfterRunningStatus extends CustomStateStatus("AFTER") {
    override def isRunning: Boolean = true
  }

  case object PreparingResourcesStatus extends CustomStateStatus("PREPARING")

  case object TestStatus extends CustomStateStatus("TEST")

}
