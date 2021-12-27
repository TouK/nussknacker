package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object EmbeddedProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsMap = Map(EmbeddedStateStatus.Restarting -> List(ProcessActionType.Cancel)),
  statusIconsMap = Map(EmbeddedStateStatus.Restarting -> "/assets/embedded/states/deploy-restart-animated.svg"),
  statusTooltipsMap = Map(EmbeddedStateStatus.Restarting -> "Scenario was deployed but now is restarting..."),
  statusDescriptionsMap = Map(EmbeddedStateStatus.Restarting -> "Scenario is restarting...")
)
