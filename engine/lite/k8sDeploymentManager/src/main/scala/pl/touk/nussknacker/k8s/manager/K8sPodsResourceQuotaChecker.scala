package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import skuber.Resource.ResourceList
import skuber.{ListResource, Resource, ResourceQuotaList}

import scala.concurrent.Future
import scala.language.reflectiveCalls

object K8sPodsResourceQuotaChecker extends LazyLogging {

  val podsResourceQuota = "pods"

  def hasReachedQuotaLimit(oldDeploymentReplicasCount: Option[Int], quotas: ResourceQuotaList, replicasCount: Int): Future[Boolean] = {
    quotas match {
      case ListResource(_, _, _, List()) => Future.successful(false)
      case ListResource(_, _, _, List(quota)) => hasReachedQuotaLimitInternal(oldDeploymentReplicasCount, quota, replicasCount)
      case _ =>
        logger.warn("Found multiple resource quotas - this should not be possible in k8s")
        Future.successful(false)
    }
  }

  private def hasReachedQuotaLimitInternal(oldDeploymentReplicasCount: Option[Int], quotas: Resource.Quota, replicasCount: Int): Future[Boolean] = {
    val status = quotas.status

    def podResourceQuotaOf(resource: Option[ResourceList]): BigDecimal = {
      resource.flatMap(_.get(podsResourceQuota)).map(_.amount).sum
    }

    val usedAmount = podResourceQuotaOf(status.map(_.used))
    val hardAmount = podResourceQuotaOf(status.map(_.hard))
    val currentDeploymentCount = BigDecimal(oldDeploymentReplicasCount.getOrElse(0))
    val requestedReplicasCount = BigDecimal(replicasCount)
    val quotaExceeded = (usedAmount + requestedReplicasCount) > (hardAmount + currentDeploymentCount)
    logger.trace(s"Scenario deployment resource quota exceed: $quotaExceeded, usedPods: $usedAmount, hardPods: $hardAmount, replicasCount: $requestedReplicasCount, currentScenarioDeploymentCount: $currentDeploymentCount")

    quotaExceeded match {
      case false => Future.successful(quotaExceeded)
      case true => throw ResourceQuotaExceededException("Quota limit exceeded")
    }
  }

  case class ResourceQuotaExceededException(message: String) extends IllegalArgumentException(message)
}