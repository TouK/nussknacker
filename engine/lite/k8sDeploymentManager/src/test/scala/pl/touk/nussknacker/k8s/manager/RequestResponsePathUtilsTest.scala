package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class RequestResponsePathUtilsTest extends AnyFunSuite with Matchers {

  private val invalidK8sServiceName = (1 to (K8sUtils.maxObjectNameLength + 10)).map(_ => "a").mkString

  test("determine default path using scenario name") {
    RequestResponsePathUtils.defaultPath(ProcessName("foo"), None) shouldEqual "foo"
  }

  test("sanitize scenario name for purpose of default path preparation") {
    val longScenarioNameWithoutNuInstanceIdDefaultPath = RequestResponsePathUtils.defaultPath(ProcessName(invalidK8sServiceName), None)
    longScenarioNameWithoutNuInstanceIdDefaultPath should contain only 'a'
    longScenarioNameWithoutNuInstanceIdDefaultPath should have length K8sUtils.maxObjectNameLength
  }

  test("ensure that after nussknacker instance prefix, service name will be still valid against object name restrictions") {
    val nuInstanceId = (1 to 10).map(_ => "b").mkString
    val longScenarioNameWithNuInstanceIdDefaultPath = RequestResponsePathUtils.defaultPath(ProcessName(invalidK8sServiceName), Some(nuInstanceId))
    ServicePreparer.serviceNameWithoutSanitization(Some(nuInstanceId), longScenarioNameWithNuInstanceIdDefaultPath) shouldEqual
      ServicePreparer.serviceName(Some(nuInstanceId), longScenarioNameWithNuInstanceIdDefaultPath)
  }

  test("replace special characters during default path preparation") {
    RequestResponsePathUtils.defaultPath(ProcessName("a żółć"), None) shouldEqual "a-x"
  }

}
