package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

// Here we use default stateDefinitions set from SimpleProcessStateDefinitionManager,
// but we want to override the behaviour of default "FAILED" state, without introducing another "failed" state:
// - custom DetailedFailedStateStatus is used to handle "FAILED" state
// - message property of DetailedFailedStateStatus is used to embellish statusTooltip
object EmbeddedProcessStateDefinitionManager
    extends OverridingProcessStateDefinitionManager(
      delegate = SimpleProcessStateDefinitionManager,
      statusActionsPF = { case SimpleStateStatus.Restarting =>
        List(ProcessActionType.Cancel)
      }
    )
