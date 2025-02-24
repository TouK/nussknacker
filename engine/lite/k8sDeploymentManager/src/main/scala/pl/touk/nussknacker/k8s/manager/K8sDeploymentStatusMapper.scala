package pl.touk.nussknacker.k8s.manager

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatusDetails, ScenarioActionName, StateStatus}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.parseVersionAnnotation
import skuber.apps.v1.Deployment
import skuber.{Container, Pod}

//Based on https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#deployment-status
object K8sDeploymentStatusMapper extends LazyLogging {

  private val availableCondition = "Available"

  private val progressingCondition = "Progressing"

  private val replicaFailureCondition = "ReplicaFailure"

  private val trueConditionStatus = "True"

  private val crashLoopBackOffReason = "CrashLoopBackOff"

  private val newReplicaSetAvailable = "NewReplicaSetAvailable"

  private[manager] def findStatusForDeploymentsAndPods(
      deployments: List[Deployment],
      pods: List[Pod]
  ): Option[DeploymentStatusDetails] = {
    deployments match {
      case Nil        => None
      case one :: Nil => Some(status(one, pods))
      case duplicates =>
        Some(
          DeploymentStatusDetails(
            ProblemStateStatus(
              description = "More than one deployment is running.",
              allowedActions = Set(ScenarioActionName.Cancel),
              tooltip = Some(s"Expected one deployment, instead: ${duplicates.map(_.metadata.name).mkString(", ")}")
            ),
            None,
            None,
          )
        )
    }
  }

  private[manager] def status(deployment: Deployment, pods: List[Pod]): DeploymentStatusDetails = {
    val status = deployment.status match {
      case None         => SimpleStateStatus.DuringDeploy
      case Some(status) => mapStatusWithPods(status, pods)
    }
    DeploymentStatusDetails(
      status = status,
      // TODO: return internal deploymentId, probably computed based on some hash to make sure that it will change only when something in scenario change
      deploymentId = None,
      version = parseVersionAnnotation(deployment).map(_.versionId),
    )
  }

  // TODO: should we add responses to status attributes?
  private[manager] def mapStatusWithPods(
      status: Deployment.Status,
      pods: List[Pod]
  ): StateStatus = {
    def condition(name: String): Option[Deployment.Condition] = status.conditions.find(cd => cd.`type` == name)
    def anyContainerInState(state: Container.State) =
      pods.flatMap(_.status.toList).flatMap(_.containerStatuses).exists(_.state.exists(_ == state))

    (condition(availableCondition), condition(progressingCondition), condition(replicaFailureCondition)) match {
      case (Some(available), None | ProgressingNewReplicaSetAvailable(), _) if isTrue(available) =>
        SimpleStateStatus.Running
      case (_, Some(progressing), _)
          if isTrue(progressing) && anyContainerInState(Container.Waiting(Some(crashLoopBackOffReason))) =>
        logger.debug(
          s"Some containers are in waiting state with CrashLoopBackOff reason - returning Restarting status. Pods: $pods"
        )
        SimpleStateStatus.Restarting
      case (_, Some(progressing), _) if isTrue(progressing) =>
        SimpleStateStatus.DuringDeploy
      case (_, _, Some(replicaFailure)) if isTrue(replicaFailure) =>
        ProblemStateStatus(
          "There are some problems with scenario.",
          tooltip = replicaFailure.message.map("Error: " + _)
        )
      case (a, b, _) =>
        ProblemStateStatus(
          "There are some problems with scenario.",
          tooltip = NonEmptyList
            .fromList(a.flatMap(_.message).toList ++ b.flatMap(_.message).toList)
            .map(_.toList.mkString("Errors: ", ", ", ""))
        )
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
