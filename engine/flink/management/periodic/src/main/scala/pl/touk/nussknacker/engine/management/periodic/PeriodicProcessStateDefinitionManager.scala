package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessStateDefinitionManager}

class PeriodicProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager) extends OverridingProcessStateDefinitionManager(
  statusActionsPF = PeriodicStateStatus.statusActionsPF,
  statusTooltipsPF = PeriodicStateStatus.statusTooltipsPF,
  statusDescriptionsPF = PeriodicStateStatus.statusDescriptionsPF,
  customStateDefinitions = PeriodicStateStatus.customStateDefinitions,
  delegate = delegate
)
