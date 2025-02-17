package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, StateStatus, StatusDetails}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.parseVersionAnnotation
import pl.touk.nussknacker.k8s.manager.K8sDeploymentStatusMapper._
import skuber.apps.v1.Deployment
import skuber.{Container, Pod}

object K8sDeploymentStatusMapper {

  private val availableCondition = "Available"

  private val progressingCondition = "Progressing"

  private val replicaFailureCondition = "ReplicaFailure"

  private val trueConditionStatus = "True"

  private val crashLoopBackOffReason = "CrashLoopBackOff"

  private val newReplicaSetAvailable = "NewReplicaSetAvailable"
}

//Based on https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#deployment-status
class K8sDeploymentStatusMapper(definitionManager: ProcessStateDefinitionManager) extends LazyLogging {

  private[manager] def findStatusForDeploymentsAndPods(
      deployments: List[Deployment],
      pods: List[Pod]
  ): Option[StatusDetails] = {
    deployments match {
      case Nil        => None
      case one :: Nil => Some(status(one, pods))
      case duplicates =>
        Some(
          StatusDetails(
            ProblemStateStatus.MultipleJobsRunning,
            None,
            errors = List(s"Expected one deployment, instead: ${duplicates.map(_.metadata.name).mkString(", ")}")
          )
        )
    }
  }

  private[manager] def status(deployment: Deployment, pods: List[Pod]): StatusDetails = {
    val (status, attrs, errors) = deployment.status match {
      case None         => (SimpleStateStatus.DuringDeploy, None, Nil)
      case Some(status) => mapStatusWithPods(status, pods)
    }
    val startTime = deployment.metadata.creationTimestamp.map(_.toInstant.toEpochMilli)
    StatusDetails(
      status,
      // TODO: return internal deploymentId, probably computed based on some hash to make sure that it will change only when something in scenario change
      None,
      None,
      parseVersionAnnotation(deployment),
      startTime,
      errors
    )
  }

  // TODO: should we add responses to status attributes?
  private[manager] def mapStatusWithPods(
      status: Deployment.Status,
      pods: List[Pod]
  ): (StateStatus, Option[Json], List[String]) = {
    def condition(name: String): Option[Deployment.Condition] = status.conditions.find(cd => cd.`type` == name)
    def anyContainerInState(state: Container.State) =
      pods.flatMap(_.status.toList).flatMap(_.containerStatuses).exists(_.state.exists(_ == state))

    (condition(availableCondition), condition(progressingCondition), condition(replicaFailureCondition)) match {
      case (Some(available), None | ProgressingNewReplicaSetAvailable(), _) if isTrue(available) =>
        (SimpleStateStatus.Running, None, Nil)
      case (_, Some(progressing), _)
          if isTrue(progressing) && anyContainerInState(Container.Waiting(Some(crashLoopBackOffReason))) =>
        logger.debug(
          s"Some containers are in waiting state with CrashLoopBackOff reason - returning Restarting status. Pods: $pods"
        )
        (SimpleStateStatus.Restarting, None, Nil)
      case (_, Some(progressing), _) if isTrue(progressing) => (SimpleStateStatus.DuringDeploy, None, Nil)
      case (_, _, Some(replicaFailure)) if isTrue(replicaFailure) =>
        (ProblemStateStatus.Failed, None, replicaFailure.message.toList)
      case (a, b, _) => (ProblemStateStatus.Failed, None, a.flatMap(_.message).toList ++ b.flatMap(_.message).toList)
    }
  }

  // https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#typical-status-properties
  // "For some conditions, True represents normal operation, and for some conditions, False represents normal operation."...
  // in our case Availability and Progressing have "positive polarity" as described in link above...
  private def isTrue(condition: Deployment.Condition) = condition.status == trueConditionStatus

  // Regarding https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#complete-deployment
  // "type: Progressing with status: "True" means that your Deployment is either in the middle of a rollout and it is progressing
  // or that it has successfully completed its progress and the minimum required new replicas are available ..."
  object ProgressingNewReplicaSetAvailable {
    def unapply(progressingCondition: Option[Deployment.Condition]): Boolean =
      progressingCondition.exists(c => isTrue(c) && c.reason.contains(newReplicaSetAvailable))
  }

}
