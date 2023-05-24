package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.OverridingProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

object FlinkProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  delegate = SimpleProcessStateDefinitionManager,
  statusActionsPF = FlinkStateStatus.statusActionsPF
)
