package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class RequestResponseSlugUtilsTest extends AnyFunSuite with Matchers {

  private val invalidK8sServiceName = (1 to (K8sUtils.maxObjectNameLength + 10)).map(_ => "a").mkString

  test("determine default slug using scenario name") {
    RequestResponseSlugUtils.defaultSlug(ProcessName("foo"), None) shouldEqual "foo"
  }

  test("sanitize scenario name for purpose of default slug preparation") {
    val longScenarioNameWithoutNuInstanceIdDefaultSlug = RequestResponseSlugUtils.defaultSlug(ProcessName(invalidK8sServiceName), None)
    longScenarioNameWithoutNuInstanceIdDefaultSlug should contain only 'a'
    longScenarioNameWithoutNuInstanceIdDefaultSlug should have length K8sUtils.maxObjectNameLength
  }

  test("ensure that after nussknacker instance prefix, service name will be still valid against object name restrictions") {
    val nuInstanceId = (1 to 10).map(_ => "b").mkString
    val longScenarioNameWithNuInstanceIdDefaultSlug = RequestResponseSlugUtils.defaultSlug(ProcessName(invalidK8sServiceName), Some(nuInstanceId))
    ServicePreparer.serviceNameWithoutSanitization(Some(nuInstanceId), longScenarioNameWithNuInstanceIdDefaultSlug) shouldEqual
      ServicePreparer.serviceName(Some(nuInstanceId), longScenarioNameWithNuInstanceIdDefaultSlug)
  }

  test("replace special characters during default slug preparation") {
    RequestResponseSlugUtils.defaultSlug(ProcessName("a żółć"), None) shouldEqual "a-x"
  }

  test("lazy determineSlug method uses the same default slug logic sanitization as eager version (determined during scenario creation)") {
    val longScenarioNameWithoutNuInstanceIdDefaultSlug = RequestResponseSlugUtils.determineSlug(ProcessName(invalidK8sServiceName), RequestResponseMetaData(None), None)
    longScenarioNameWithoutNuInstanceIdDefaultSlug should contain only 'a'
    longScenarioNameWithoutNuInstanceIdDefaultSlug should have length K8sUtils.maxObjectNameLength
  }

}
