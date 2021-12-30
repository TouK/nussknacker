package pl.touk.nussknacker.k8s.manager

import io.circe.Json
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.parseVersionAnnotation
import pl.touk.nussknacker.k8s.manager.K8sDeploymentStatusMapper.{availableCondition, progressingCondition, trueConditionStatus}
import skuber.apps.v1.Deployment

object K8sDeploymentStatusMapper {

  private val availableCondition = "Available"

  private val progressingCondition = "Progressing"

  private val trueConditionStatus = "True"

}

//Based on https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#deployment-status
class K8sDeploymentStatusMapper(definitionManager: ProcessStateDefinitionManager) {

  private[manager] def findStatusForDeployments(deployments: List[Deployment]): Option[ProcessState] = {
    deployments match {
      case Nil => None
      case one :: Nil => Some(status(one))
      case duplicates =>
        val errors = List(s"Expected one deployment, instead: ${duplicates.map(_.metadata.name).mkString(", ")}")
        Some(ProcessState(None, K8sStateStatus.MultipleJobsRunning, None, definitionManager, None, None, errors))
    }
  }

  private def status(deployment: Deployment): ProcessState = {
    val (status, attrs, errors) = deployment.status match {
      case None => (SimpleStateStatus.DuringDeploy, None, Nil)
      case Some(status) => mapStatus(status)
    }
    val startTime = deployment.metadata.creationTimestamp.map(_.toInstant.toEpochMilli)
    ProcessState(None, status, parseVersionAnnotation(deployment), definitionManager, startTime, attrs, errors)
  }

  //TODO: should we add responses to status attributes?
  private[manager] def mapStatus(status: Deployment.Status): (StateStatus, Option[Json], List[String]) = {
    def condition(name: String): Option[Deployment.Condition] = status.conditions.find(cd => cd.`type` == name)
    (condition(availableCondition), condition(progressingCondition)) match {
      case (Some(available), _) if isTrue(available) => (SimpleStateStatus.Running, None, Nil)
      case (_, Some(progressing)) if isTrue(progressing) => (SimpleStateStatus.DuringDeploy, None, Nil)
      case (a, b) => (SimpleStateStatus.Failed, None, a.flatMap(_.message).toList ++ b.flatMap(_.message).toList)
    }
  }

  //https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#typical-status-properties
  //"For some conditions, True represents normal operation, and for some conditions, False represents normal operation."...
  //in our case Availability and Progressing have "positive polarity" as described in link above...
  private def isTrue(condition: Deployment.Condition) = condition.status == trueConditionStatus

}
