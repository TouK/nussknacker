package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object FlinkProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsPF = Map(
    FlinkStateStatus.Restarting -> List(ProcessActionType.Cancel),
    FlinkStateStatus.MultipleJobsRunning -> List(ProcessActionType.Cancel)
  ),
  statusIconsPF = Map(FlinkStateStatus.Restarting -> "/assets/flink/states/deploy-restart-animated.svg"),
  statusTooltipsPF = Map(FlinkStateStatus.Restarting -> "Scenario was deployed but now is restarting..."),
  statusDescriptionsPF = Map(FlinkStateStatus.Restarting -> "Scenario is restarting...")
)
