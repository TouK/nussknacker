package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class PathUtilsTest extends AnyFunSuite with Matchers {

  private val invalidK8sServiceName = (1 to 70).map(_ => "a").mkString // 63 is the limit

  test("determine default path using scenario name") {
    PathUtils.defaultPath(ProcessName("foo"), None) shouldEqual "foo"

    val longScenarioNameWithoutNuInstanceIdDefaultPath = PathUtils.defaultPath(ProcessName(invalidK8sServiceName), None)
    longScenarioNameWithoutNuInstanceIdDefaultPath should contain only 'a'
    longScenarioNameWithoutNuInstanceIdDefaultPath should have length 63

    val nuInstanceId = (1 to 10).map(_ => "b").mkString
    val longScenarioNameWithNuInstanceIdDefaultPath = PathUtils.defaultPath(ProcessName(invalidK8sServiceName), Some(nuInstanceId))
    ServicePreparer.serviceNameWithoutSanitization(Some(nuInstanceId), longScenarioNameWithNuInstanceIdDefaultPath) shouldEqual
      ServicePreparer.serviceName(Some(nuInstanceId), longScenarioNameWithNuInstanceIdDefaultPath)
  }

}
