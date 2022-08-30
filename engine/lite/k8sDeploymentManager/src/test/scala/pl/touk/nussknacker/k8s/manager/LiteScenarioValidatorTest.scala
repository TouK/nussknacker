package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder

class LiteScenarioValidatorTest extends AnyFunSuite with Matchers {

  private val validSlug = "asdf"
  private val invalidK8sServiceName = (1 to (K8sUtils.maxObjectNameLength + 10)).map(_ => "a").mkString
  private val noInstanceNameValidator = new LiteScenarioValidator(None)

  test("return ok for streaming scenarios") {
    val scenarioWithLongName = ScenarioBuilder.streamingLite(invalidK8sServiceName)
      .source("source", "dumb")
      .emptySink("sink", "dumb")
      .toCanonicalProcess
    noInstanceNameValidator.validate(scenarioWithLongName)
  }

  test("validate against service name for not defined instance name") {
    val scenarioWithLongName = ScenarioBuilder.requestResponse(invalidK8sServiceName)
      .source("source", "dumb")
      .emptySink("sink", "dumb")
      .toCanonicalProcess
    noInstanceNameValidator.validate(scenarioWithLongName) shouldBe 'invalid
    noInstanceNameValidator.validateRequestResponse(ProcessName(validSlug), RequestResponseMetaData(None)) shouldBe 'valid
    noInstanceNameValidator.validateRequestResponse(ProcessName(invalidK8sServiceName), RequestResponseMetaData(None)) shouldBe 'invalid
    noInstanceNameValidator.validateRequestResponse(ProcessName(validSlug), RequestResponseMetaData(Some(invalidK8sServiceName))) shouldBe 'invalid
  }

  test("validate against service name for defined instance name") {
    val nussknackerInstanceName = (1 to (K8sUtils.maxObjectNameLength - 3)).map(_ => "a").mkString
    val longInstanceNameValidator = new LiteScenarioValidator(Some(nussknackerInstanceName))
    longInstanceNameValidator.validateRequestResponse(ProcessName("a"), RequestResponseMetaData(None)) shouldBe 'valid
    longInstanceNameValidator.validateRequestResponse(ProcessName("aaaa"), RequestResponseMetaData(None)) shouldBe 'invalid
  }

}
