package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.OverridingProcessStateDefinitionManager

object FlinkProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsPF = FlinkStateStatus.statusActionsPF,
  stateDefinitions = FlinkStateStatus.customStateDefinitions
)
