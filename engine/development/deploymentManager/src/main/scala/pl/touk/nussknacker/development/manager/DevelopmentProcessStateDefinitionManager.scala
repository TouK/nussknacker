package pl.touk.nussknacker.development.manager

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{CustomStateStatus, OverridingProcessStateDefinitionManager, ProcessActionType, ProcessStateDefinitionManager, StateDefinition, StateStatus}

class DevelopmentProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager) extends OverridingProcessStateDefinitionManager(
  statusActionsPF = DevelopmentStateStatus.statusActionsPF,
  stateDefinitions = DevelopmentStateStatus.customStateDefinitions,
  delegate = delegate
)

object DevelopmentStateStatus {

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case DevelopmentStateStatus.AfterRunningStatus => List(ProcessActionType.Cancel)
    case DevelopmentStateStatus.PreparingResourcesStatus => List(ProcessActionType.Deploy)
    case DevelopmentStateStatus.TestStatus => List(ProcessActionType.Deploy)
  }

  val customStateDefinitions: Set[StateDefinition] = Set(
    StateDefinition(
      name = AfterRunningStatus.name,
      displayableName = "After running",
      icon = None,
      tooltip = Some("External running."),
      description = Some("External running.")
    ),
    StateDefinition(
      name = PreparingResourcesStatus.name,
      displayableName = "Preparing resources",
      icon = None,
      tooltip = Some("Preparing external resources."),
      description = Some("Preparing external resources.")
    ),
    StateDefinition(
      name = TestStatus.name,
      displayableName = "Test",
      icon = None,
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
