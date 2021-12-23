package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object FlinkProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsMap = Map(
    FlinkStateStatus.Restarting -> List(ProcessActionType.Cancel),
    FlinkStateStatus.MultipleJobsRunning -> List(ProcessActionType.Cancel)
  ),
  statusIconsMap = Map(FlinkStateStatus.Restarting -> "/assets/flink/states/deploy-restart-animated.svg"),
  statusTooltipsMap = Map(FlinkStateStatus.Restarting -> "Scenario was deployed but now is restarting..."),
  statusDescriptionsMap = Map(FlinkStateStatus.Restarting -> "Scenario is restarting...")
)
