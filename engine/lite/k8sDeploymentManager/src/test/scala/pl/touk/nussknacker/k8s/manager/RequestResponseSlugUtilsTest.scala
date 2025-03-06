package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class RequestResponseSlugUtilsTest extends AnyFunSuite with Matchers {

  private val invalidK8sServiceName = (1 to (K8sUtils.maxObjectNameLength + 10)).map(_ => "a").mkString

  test("determine default slug using scenario name") {
    RequestResponseSlugUtils.defaultSlug(ProcessName("foo"), OptionalNussknackerInstanceName.empty) shouldEqual "foo"
  }

  test("sanitize scenario name for purpose of default slug preparation") {
    val longScenarioNameWithoutNuInstanceIdDefaultSlug =
      RequestResponseSlugUtils.defaultSlug(ProcessName(invalidK8sServiceName), OptionalNussknackerInstanceName.empty)
    longScenarioNameWithoutNuInstanceIdDefaultSlug should contain only 'a'
    longScenarioNameWithoutNuInstanceIdDefaultSlug should have length K8sUtils.maxObjectNameLength
  }

  test(
    "ensure that after nussknacker instance prefix, service name will be still valid against object name restrictions"
  ) {
    val nuInstanceId = (1 to 10).map(_ => "b").mkString
    val longScenarioNameWithNuInstanceIdDefaultSlug =
      RequestResponseSlugUtils.defaultSlug(
        ProcessName(invalidK8sServiceName),
        OptionalNussknackerInstanceName.forInstanceName(nuInstanceId)
      )
    ServicePreparer.serviceNameWithoutSanitization(
      OptionalNussknackerInstanceName.forInstanceName(nuInstanceId),
      longScenarioNameWithNuInstanceIdDefaultSlug
    ) shouldEqual
      ServicePreparer.serviceName(
        OptionalNussknackerInstanceName.forInstanceName(nuInstanceId),
        longScenarioNameWithNuInstanceIdDefaultSlug
      )
  }

  test("replace special characters during default slug preparation") {
    RequestResponseSlugUtils.defaultSlug(ProcessName("a żółć"), OptionalNussknackerInstanceName.empty) shouldEqual "a-x"
  }

  test(
    "lazy determineSlug method uses the same default slug logic sanitization as eager version (determined during scenario creation)"
  ) {
    val longScenarioNameWithoutNuInstanceIdDefaultSlug =
      RequestResponseSlugUtils.determineSlug(
        ProcessName(invalidK8sServiceName),
        RequestResponseMetaData(None),
        OptionalNussknackerInstanceName.empty
      )
    longScenarioNameWithoutNuInstanceIdDefaultSlug should contain only 'a'
    longScenarioNameWithoutNuInstanceIdDefaultSlug should have length K8sUtils.maxObjectNameLength
  }

  test("should generate slug which is a correct service name for long scenario name and given instance name") {
    val longScenarioName = ProcessName((0.to(100)).map(_ => "x").mkString)
    RequestResponseSlugUtils
      .determineSlug(
        longScenarioName,
        RequestResponseMetaData(None),
        OptionalNussknackerInstanceName.forInstanceName("nussknacker")
      )
      .length shouldBe K8sUtils.maxObjectNameLength - "nussknacker-".length
  }

  test(
    "should generate slug which is a correct service name for scenario name with non alphanumerics and given instance name"
  ) {
    val longScenarioName = ProcessName(0.to(100).map(_ => "_").mkString)
    val determinedSlug =
      RequestResponseSlugUtils.determineSlug(
        longScenarioName,
        RequestResponseMetaData(None),
        OptionalNussknackerInstanceName.forInstanceName("nussknacker")
      )
    // we add instance name (nussknacker-) at the beginning, so first character is not sanitized
    determinedSlug.head.isLetterOrDigit shouldBe false
    determinedSlug.last.isLetterOrDigit shouldBe true
  }

}
