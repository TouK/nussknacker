package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager}
import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessState}
import skuber.apps.v1.Deployment

object K8sProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  delegate = SimpleProcessStateDefinitionManager
) {

  def errorMultipleJobsRunning(duplicates: List[Deployment]): ProcessState = {
    processState(ProblemStateStatus.multipleJobsRunning)
      .copy(
        errors = List(s"Expected one deployment, instead: ${duplicates.map(_.metadata.name).mkString(", ")}")
      )
  }
}
