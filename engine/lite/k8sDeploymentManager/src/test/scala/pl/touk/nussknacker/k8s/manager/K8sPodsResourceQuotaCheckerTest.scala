package pl.touk.nussknacker.k8s.manager

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.k8s.manager.K8sPodsResourceQuotaChecker.{ResourceQuotaExceededException, podsResourceQuota}
import skuber.Resource.Quota
import skuber.{ListResource, Resource}

class K8sPodsResourceQuotaCheckerTest extends FunSuite {

  test("should not exceed limit when no quotas defined") {
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, ListResource("", "", None, List[Resource.Quota]()), 1)
    quotaExceeded shouldEqual Valid(())
  }

  test("should not exceed limit when quotas defined but number of replicas is lower") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 2), used = Map(podsResourceQuota -> 0))))
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, ListResource("", "", None, List[Resource.Quota](quota)), 1)
    quotaExceeded shouldEqual Valid(())
  }

  test("should not exceed limit when some quotas are used but number of replicas is lower") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 2))))
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, ListResource("", "", None, List[Resource.Quota](quota)), 1)
    quotaExceeded shouldEqual Valid(())
  }

  test("should exceed quota limit when number of replicas is higher") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 1))))
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, ListResource("", "", None, List[Resource.Quota](quota)), 5)
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException("Not enough free resources on the K8 cluster. Decrease parallelism or release cluster resources."))
  }

  test("should not exceed quota limit when redeploying same number of replicas") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 2))))
    val oldDeploymentReplicasCount = Some(2)
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(oldDeploymentReplicasCount, ListResource("", "", None, List[Resource.Quota](quota)), 2)
    quotaExceeded shouldEqual Valid(())
  }

  test("should exceed quota limit when redeploying with bigger number of replicas") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 2))))
    val oldDeploymentReplicasCount = Some(2)
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(oldDeploymentReplicasCount, ListResource("", "", None, List[Resource.Quota](quota)), 4)
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException("Not enough free resources on the K8 cluster. Decrease parallelism or release cluster resources."))
  }

  test("should exceed quota limit when deploying with parallelism = 1 and cluster is full") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 3), used = Map(podsResourceQuota -> 3))))
    val oldDeploymentReplicasCount = Some(0)
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(oldDeploymentReplicasCount, ListResource("", "", None, List[Resource.Quota](quota)), 1)
    quotaExceeded shouldBe Invalid(ResourceQuotaExceededException("Cluster is full. Release some cluster resources."))
  }

  test("should not exceed limit when found many quota in namespace") {
    val quota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 1), used = Map(podsResourceQuota -> 1))))
    val anotherQuota = Resource.Quota(status = Some(Quota.Status(hard = Map(podsResourceQuota -> 1), used = Map(podsResourceQuota -> 1))))
    val quotaExceeded = K8sPodsResourceQuotaChecker.hasReachedQuotaLimit(None, ListResource("", "", None, List[Resource.Quota](quota, anotherQuota)), 1)
    quotaExceeded shouldEqual Valid(())
  }
}
