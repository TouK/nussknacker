package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object EmbeddedProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsPF = {
    case EmbeddedStateStatus.Restarting => List(ProcessActionType.Cancel)
    // We don't know if it is temporal problem or not so deploy is still available
    case  EmbeddedStateStatus.DetailedFailedStateStatus(_) => List(ProcessActionType.Deploy, ProcessActionType.Cancel)
  },
  statusIconsPF = {
    case EmbeddedStateStatus.Restarting => "/assets/embedded/states/deploy-restart-animated.svg"
    case EmbeddedStateStatus.DetailedFailedStateStatus(_) => "/assets/states/failed.svg"
  },
  statusTooltipsPF = {
    case EmbeddedStateStatus.Restarting => "Scenario was deployed but now is restarting..."
    case EmbeddedStateStatus.DetailedFailedStateStatus(message) => s"Problems detected: $message"
  },
  statusDescriptionsPF = {
    case EmbeddedStateStatus.Restarting => "Scenario is restarting..."
    case EmbeddedStateStatus.DetailedFailedStateStatus(_) => "There are some problems with scenario."
  }
)
