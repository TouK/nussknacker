package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessActionType}

object K8sProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  statusActionsPF = {
    case K8sStateStatus.MultipleJobsRunning => List(ProcessActionType.Cancel)
  }
)
