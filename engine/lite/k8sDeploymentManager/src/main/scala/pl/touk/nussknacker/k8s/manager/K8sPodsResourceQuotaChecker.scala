package pl.touk.nussknacker.k8s.manager

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import com.typesafe.scalalogging.LazyLogging
import skuber.{ListResource, Resource, ResourceQuotaList}
import skuber.Resource.ResourceList
import skuber.apps.v1.Deployment.{RollingUpdate, Strategy}

import scala.language.reflectiveCalls
import scala.util.Try

object K8sPodsResourceQuotaChecker extends LazyLogging {

  val podsResourceQuota = "pods"

  def hasReachedQuotaLimit(
      oldDeploymentReplicasCount: Option[Int],
      quotas: ResourceQuotaList,
      replicasCount: Int,
      strategy: Option[Strategy]
  ): Validated[Throwable, Unit] = {
    quotas match {
      case ListResource(_, _, _, List()) => valid(())
      case ListResource(_, _, _, List(quota)) =>
        hasReachedQuotaLimitInternal(oldDeploymentReplicasCount, quota, replicasCount, strategy)
      case _ =>
        logger.warn("More than one resource quota is not supported")
        valid(())
    }
  }

  private def hasReachedQuotaLimitInternal(
      oldDeploymentReplicasCount: Option[Int],
      quotas: Resource.Quota,
      replicasCount: Int,
      strategy: Option[Strategy]
  ): Validated[Throwable, Unit] = {
    val status = quotas.status

    def podResourceQuotaOf(resource: Option[ResourceList]): BigDecimal = {
      resource.flatMap(_.get(podsResourceQuota)).map(_.amount).sum
    }

    val usedAmount             = podResourceQuotaOf(status.map(_.used))
    val hardAmount             = podResourceQuotaOf(status.map(_.hard))
    val currentDeploymentCount = BigDecimal(oldDeploymentReplicasCount.getOrElse(0))
    val requestedReplicasCount = BigDecimal(replicasCount)
    val maxSurge               = calculateMaxSurge(replicasCount, strategy)
    val quotaExceeded          = (usedAmount - currentDeploymentCount + requestedReplicasCount + maxSurge) > hardAmount
    logger.trace(
      s"Scenario deployment resource quota exceed: $quotaExceeded, usedPods: $usedAmount, hardPods: $hardAmount, replicasCount: $requestedReplicasCount, currentScenarioDeploymentCount: $currentDeploymentCount"
    )

    if (quotaExceeded) {
      val error = usedAmount match {
        case `hardAmount` => ResourceQuotaExceededException.fullCluster
        case _            => ResourceQuotaExceededException.notEnoughResources
      }
      invalid(error)
    } else {
      valid(())
    }
  }

  private def calculateMaxSurge(replicasCount: Int, strategy: Option[Strategy]): Int = {
    strategy.flatMap(_.rollingUpdate) match {
      case Some(RollingUpdate(_, Left(intSurge))) =>
        intSurge
      case Some(RollingUpdate(_, Right(percentString))) =>
        val percent = Try(percentString.replaceAll("[%\\s]*", "").toDouble).getOrElse(0d) / 100
        math.ceil(replicasCount * percent).toInt
      case _ =>
        0
    }
  }

  object ResourceQuotaExceededException {

    val fullCluster: ResourceQuotaExceededException = ResourceQuotaExceededException(
      "Cluster is full. Release some cluster resources."
    )

    val notEnoughResources: ResourceQuotaExceededException = ResourceQuotaExceededException(
      "Not enough free resources on the K8 cluster. Decrease parallelism or release cluster resources."
    )

  }

  case class ResourceQuotaExceededException(message: String) extends IllegalArgumentException(message)
}
