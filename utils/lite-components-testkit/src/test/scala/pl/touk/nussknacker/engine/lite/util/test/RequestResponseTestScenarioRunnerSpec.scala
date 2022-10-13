package pl.touk.nussknacker.engine.lite.util.test

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentInfo, ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.concurrent.Future

class RequestResponseTestScenarioRunnerSpec extends AnyFunSuite with Matchers {

  private val failingComponent = "failing"

  private val scenarioRunner: RequestResponseTestScenarioRunner = TestScenarioRunner
    .requestResponseBased()
    .withExtraComponents(List(ComponentDefinition(failingComponent, FailingService)))
    .build()

  test("runs tests") {
    val runner = scenarioRunner
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = RequestResponseTestScenarioRunner.sampleSchemas)
      .source("input", "request")
      .emptySink("output", "response", "Raw editor" -> "true", "Value" -> "{field1: #input.field1 + '-suffix'}")

    runner.runWithRequests(scenario) { invoker =>
      invoker(HttpRequest(HttpMethods.POST, entity = Map("field1" -> "value").asJson.spaces2)) shouldBe Right(
        Map("field1" -> "value-suffix").asJson
      )
    }

  }

  test("runs failure handling") {
    val runner = scenarioRunner
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = RequestResponseTestScenarioRunner.sampleSchemas)
      .source("input", "request")
      .enricher("fail", "output", failingComponent, "value" -> "#input.field1")
      .emptySink("output", "response", "value" -> "#output")

    runner.runWithRequests(scenario) { invoker =>
      val firstError = invoker(
        HttpRequest(HttpMethods.POST, entity = Map("field1" -> FailingService.failTrigger).asJson.spaces2)
      ).left.get.head

      firstError.nodeComponentInfo shouldBe Some(
        NodeComponentInfo("fail", Some(ComponentInfo(failingComponent, ComponentType.Enricher)))
      )
      firstError.throwable.getMessage shouldBe FailingService.failMessage

    }

  }

}

object FailingService extends Service {

  val failTrigger = "FAIL!!!"

  val failMessage = "Should fail"

  @MethodToInvoke
  def run(@ParamName("value") value: String): Future[String] = value match {
    case `failTrigger` => Future.failed(new IllegalArgumentException(failMessage))
    case _             => Future.successful("OK")
  }
}
