package pl.touk.nussknacker.k8s.manager

import io.circe.Json
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.scenarioVersionAnnotation
import skuber.apps.v1.Deployment

//Based on https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#deployment-status
class K8sDeploymentStatusMapper(definitionManager: ProcessStateDefinitionManager) {

  private val availableCondition = "Available"

  private val progressingCondition = "Progressing"

  //https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#typical-status-properties
  //"For some conditions, True represents normal operation, and for some conditions, False represents normal operation."...
  //in our case Availability and Progressing have "positive polarity" as described in link above...
  private val conditionStatusToCheck = "True"

  private[manager] def findStatusForDeployments(deployments: List[Deployment]): Option[ProcessState] = {
    def status(deployment: Deployment): ProcessState = {
      val version = deployment.metadata.annotations.get(scenarioVersionAnnotation)
        .flatMap(CirceUtil.decodeJson[ProcessVersion](_).toOption)
      val (status, attrs, errors) = deployment.status match {
        case None => (SimpleStateStatus.DuringDeploy, None, Nil)
        case Some(status) => mapStatus(status)
      }
      val startTime = deployment.metadata.creationTimestamp.map(_.toInstant.toEpochMilli)
      ProcessState(None, status, version, definitionManager, startTime, attrs, errors)
    }
    deployments match {
      case Nil => None
      case one :: Nil => Some(status(one))
      case duplicates =>
        val errors = List(s"Expected one deployment, instead: ${duplicates.map(_.metadata.name).mkString(", ")}")
        Some(ProcessState(None, K8sStateStatus.MultipleJobsRunning, None, definitionManager, None, None, errors))
    }
  }

  //TODO: should we add responses to status attributes?
  private[manager] def mapStatus(status: Deployment.Status): (StateStatus, Option[Json], List[String]) = {
    def trueCondition(name: String): Option[(Deployment.Condition, Boolean)] = status.conditions
      .find(cd => cd.`type` == name)
      .map(k => (k, k.status == conditionStatusToCheck))

    (trueCondition(availableCondition), trueCondition(progressingCondition)) match {
      case (Some((_, true)), _) => (SimpleStateStatus.Running, None, Nil)
      case (_, Some((_, true))) => (SimpleStateStatus.DuringDeploy, None, Nil)
      case (a, b) => (SimpleStateStatus.Failed, None, a.flatMap(_._1.message).toList ++ b.flatMap(_._1.message).toList)
    }
  }

}
