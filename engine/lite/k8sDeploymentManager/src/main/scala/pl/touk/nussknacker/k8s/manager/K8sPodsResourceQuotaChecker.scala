package pl.touk.nussknacker.k8s.manager

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import skuber.Resource.ResourceList
import skuber.{ListResource, Resource, ResourceQuotaList}

import scala.language.reflectiveCalls

object K8sPodsResourceQuotaChecker extends LazyLogging {

  val podsResourceQuota = "pods"

  def hasReachedQuotaLimit(oldDeploymentReplicasCount: Option[Int], quotas: ResourceQuotaList, replicasCount: Int): Validated[Throwable, Unit] = {
    quotas match {
      case ListResource(_, _, _, List()) => valid(Unit)
      case ListResource(_, _, _, List(quota)) => hasReachedQuotaLimitInternal(oldDeploymentReplicasCount, quota, replicasCount)
      case _ =>
        logger.warn("More than one resource quota is not supported")
        valid(Unit)
    }
  }

  private def hasReachedQuotaLimitInternal(oldDeploymentReplicasCount: Option[Int], quotas: Resource.Quota, replicasCount: Int): Validated[Throwable, Unit] = {
    val status = quotas.status

    def podResourceQuotaOf(resource: Option[ResourceList]): BigDecimal = {
      resource.flatMap(_.get(podsResourceQuota)).map(_.amount).sum
    }

    val usedAmount = podResourceQuotaOf(status.map(_.used))
    val hardAmount = podResourceQuotaOf(status.map(_.hard))
    val currentDeploymentCount = BigDecimal(oldDeploymentReplicasCount.getOrElse(0))
    val requestedReplicasCount = BigDecimal(replicasCount)
    val quotaExceeded = (usedAmount - currentDeploymentCount + requestedReplicasCount) > hardAmount
    logger.trace(s"Scenario deployment resource quota exceed: $quotaExceeded, usedPods: $usedAmount, hardPods: $hardAmount, replicasCount: $requestedReplicasCount, currentScenarioDeploymentCount: $currentDeploymentCount")

    if (quotaExceeded) {
      val possibleSolutionMsg = usedAmount match {
        case `hardAmount` => "Cluster is full. Release some cluster resources."
        case _ =>  "Not enough free resources on the K8 cluster. Decrease parallelism or release cluster resources."
      }
      invalid(ResourceQuotaExceededException(possibleSolutionMsg))
    } else {
      valid(Unit)
    }
  }

  case class ResourceQuotaExceededException(message: String) extends IllegalArgumentException(message)
}