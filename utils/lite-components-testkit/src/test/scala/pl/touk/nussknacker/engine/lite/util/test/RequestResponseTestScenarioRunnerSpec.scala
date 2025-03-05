package pl.touk.nussknacker.engine.lite.util.test

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentId, ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.functions.DateUtils
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future

class RequestResponseTestScenarioRunnerSpec extends AnyFunSuite with Matchers {

  private val failingComponent = "failing"

  private val baseRunner: RequestResponseTestScenarioRunner =
    TestScenarioRunner
      .requestResponseBased()
      .withExtraComponents(List(ComponentDefinition(failingComponent, FailingService)))
      .build()

  test("runs tests") {
    val runner = baseRunner
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = RequestResponseTestScenarioRunner.sampleSchemas)
      .source("input", "request")
      .emptySink(
        "output",
        "response",
        "Raw editor" -> "true".spel,
        "Value"      -> "{field1: #input.field1 + '-suffix'}".spel
      )

    val runResults = runner.runWithRequests(scenario) { invoker =>
      invoker(HttpRequest(HttpMethods.POST, entity = Map("field1" -> "value").asJson.spaces2)) shouldBe Right(
        Map("field1" -> "value-suffix").asJson
      )
    }

    runResults.isValid shouldBe true // FIXME: verify false positives
  }

  test("runs failure handling") {
    val runner = baseRunner
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = RequestResponseTestScenarioRunner.sampleSchemas)
      .source("input", "request")
      .enricher("fail", "output", failingComponent, "value" -> "#input.field1".spel)
      .emptySink("output", "response", "value" -> "#output".spel)

    val runResults = runner.runWithRequests(scenario) { invoker =>
      val firstError = invoker(
        HttpRequest(HttpMethods.POST, entity = Map("field1" -> FailingService.failTrigger).asJson.spaces2)
      ).swap.toOption.get.head

      firstError.nodeComponentInfo shouldBe Some(
        NodeComponentInfo("fail", Some(ComponentId(ComponentType.Service, failingComponent)))
      )
      firstError.throwable.getMessage shouldBe FailingService.failMessage

    }

    runResults.isValid shouldBe true // FIXME: verify false positives
  }

  test("should return service invoke value") {
    val input = "input"
    val scenario =
      ScenarioBuilder
        .requestResponse("test")
        .additionalFields(properties = Map("inputSchema" -> stringFieldSchema, "outputSchema" -> trueFieldSchema))
        .source("input", "request")
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input.field1".spel)
        .emptySink("output", "response", "Raw editor" -> "true".spel, "Value" -> "{field1: #output}".spel)

    val runResults =
      TestScenarioRunner
        .requestResponseBased()
        .withExtraComponents(List(ComponentDefinition(TestService.ServiceId, TestService)))
        .build()
        .runWithRequests(scenario) { invoker =>
          val result = invoker(HttpRequest(HttpMethods.POST, entity = Map("field1" -> input).asJson.spaces2))
          result shouldBe Right(
            Map("field1" -> input).asJson
          )
        }

    runResults.isValid shouldBe true // FIXME: verify false positives
  }

  test("should return service invoke mocked value for test runtime mode") {
    val input = "input"
    val scenario =
      ScenarioBuilder
        .requestResponse("test")
        .additionalFields(properties = Map("inputSchema" -> stringFieldSchema, "outputSchema" -> trueFieldSchema))
        .source("input", "request")
        .enricher("service", "output", TestService.ServiceId, "param" -> "#input.field1".spel)
        .emptySink("output", "response", "Raw editor" -> "true".spel, "Value" -> "{field1: #output}".spel)

    val runResults =
      TestScenarioRunner
        .requestResponseBased()
        .withExtraComponents(List(ComponentDefinition(TestService.ServiceId, TestService)))
        .inTestRuntimeMode
        .build()
        .runWithRequests(scenario) { invoker =>
          val result = invoker(HttpRequest(HttpMethods.POST, entity = Map("field1" -> input).asJson.spaces2))
          result shouldBe Right(
            Map("field1" -> TestService.MockedValued).asJson
          )
        }

    runResults.isValid shouldBe true // FIXME: verify false positives
  }

  test("should allowing use global variable - date helper") {
    val now        = Instant.now()
    val dateHelper = new DateUtils(Clock.fixed(now, ZoneId.systemDefault()))

    val scenario =
      ScenarioBuilder
        .requestResponse("test")
        .additionalFields(properties = Map("inputSchema" -> stringFieldSchema, "outputSchema" -> trueFieldSchema))
        .source("input", "request")
        .emptySink("output", "response", "Raw editor" -> "true".spel, "Value" -> "{field1: #DATE.now.toString}".spel)

    val runResults =
      TestScenarioRunner
        .requestResponseBased()
        .withExtraGlobalVariables(Map("DATE" -> dateHelper))
        .build()
        .runWithRequests(scenario) { invoker =>
          val result = invoker(HttpRequest(HttpMethods.POST, entity = Map("field1" -> "input").asJson.spaces2))
          result shouldBe Right(
            Map("field1" -> now.toString).asJson
          )
        }

    runResults.isValid shouldBe true // FIXME: verify false positives
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
