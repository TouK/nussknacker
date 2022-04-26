package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object FlinkProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsPF = Map(
    SimpleStateStatus.DuringDeploy -> List(ProcessActionType.Cancel),
    SimpleStateStatus.Restarting -> List(ProcessActionType.Cancel),
    FlinkStateStatus.MultipleJobsRunning -> List(ProcessActionType.Cancel)
  )
)
