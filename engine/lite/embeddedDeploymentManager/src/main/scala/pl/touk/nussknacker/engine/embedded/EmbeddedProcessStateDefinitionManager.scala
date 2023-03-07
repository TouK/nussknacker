package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object EmbeddedProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsPF = {
    case SimpleStateStatus.Restarting => List(ProcessActionType.Cancel)
    // We don't know if it is temporal problem or not so deploy is still available
    case EmbeddedStateStatus.DetailedFailedStateStatus(_) => List(ProcessActionType.Deploy, ProcessActionType.Cancel)
  },
  statusTooltipsPF = {
    case EmbeddedStateStatus.DetailedFailedStateStatus(message) => Some(s"Problems detected: $message")
  },
  stateDefinitions = EmbeddedStateStatus.customStateDefinitions
)
