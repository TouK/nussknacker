package pl.touk.nussknacker.k8s.manager

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.k8s.manager.K8sPodsResourceQuotaChecker.{podsResourceQuota, ResourceQuotaExceededException}
import skuber.{ListResource, Resource}
import skuber.Resource.Quota
import skuber.apps.v1.Deployment.{RollingUpdate, Strategy}

class K8sPodsResourceQuotaCheckerTest extends AnyFunSuite {

  private def quotaList(quotas: Resource.Quota*) = ListResource("", "", None, quotas.toList)

  test("should not exceed limit when no quotas defined") {
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(
      None,
      ListResource("", "", None, List[Resource.Quota]()),
      1,
      None
    )
    quotaExceeded shouldEqual Valid(())
  }

  test("should not exceed limit when quotas defined but number of replicas is lower") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 2), used = Map(podsResourceQuota -> 0)))
    )
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, quotaList(quota), 1, None)
    quotaExceeded shouldEqual Valid(())
  }

  test("should not exceed limit when some quotas are used but number of replicas is lower") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 2)))
    )
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, quotaList(quota), 1, None)
    quotaExceeded shouldEqual Valid(())
  }

  test("should exceed quota limit when number of replicas is higher") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 1)))
    )
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, quotaList(quota), 5, None)
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException.notEnoughResources)
  }

  test("should not exceed quota limit when redeploying same number of replicas") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 2)))
    )
    val oldDeploymentReplicasCount = Some(2)
    val quotaExceeded =
      K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(oldDeploymentReplicasCount, quotaList(quota), 2, None)
    quotaExceeded shouldEqual Valid(())
  }

  test("should exceed quota limit when redeploying with bigger number of replicas") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 2)))
    )
    val oldDeploymentReplicasCount = Some(2)
    val quotaExceeded =
      K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(oldDeploymentReplicasCount, quotaList(quota), 4, None)
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException.notEnoughResources)
  }

  test("should exceed quota limit when redeploying with bigger number of replicas and bigger surge as int") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 1), used = Map(podsResourceQuota -> 1)))
    )
    val oldDeploymentReplicasCount = Some(1)
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(
      oldDeploymentReplicasCount,
      quotaList(quota),
      1,
      Some(Strategy(RollingUpdate(maxSurge = Left(1))))
    )
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException.fullCluster)
  }

  test("should exceed quota limit when redeploying with bigger number of replicas and bigger surge as percent") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 1), used = Map(podsResourceQuota -> 1)))
    )
    val oldDeploymentReplicasCount = Some(1)
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(
      oldDeploymentReplicasCount,
      quotaList(quota),
      1,
      Some(Strategy(RollingUpdate(maxSurge = Right("25 %"))))
    )
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException.fullCluster)
  }

  test("should exceed quota limit when deploying with parallelism = 1 and cluster is full") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 3)))
    )
    val oldDeploymentReplicasCount = Some(0)
    val quotaExceeded =
      K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(oldDeploymentReplicasCount, quotaList(quota), 1, None)
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException.fullCluster)
  }

  test("should not exceed limit when found many quota in namespace") {
    val quota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 1), used = Map(podsResourceQuota -> 1)))
    )
    val anotherQuota = Resource.Quota(status =
      Some(Quota.Status(hard = Map(podsResourceQuota -> 1), used = Map(podsResourceQuota -> 1)))
    )
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, quotaList(quota, anotherQuota), 1, None)
    quotaExceeded shouldEqual Valid(())
  }

}
