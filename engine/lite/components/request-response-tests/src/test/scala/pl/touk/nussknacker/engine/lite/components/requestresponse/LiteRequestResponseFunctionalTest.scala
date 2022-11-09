package pl.touk.nussknacker.engine.lite.components.requestresponse

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import io.circe.syntax.EncoderOps
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class LiteRequestResponseFunctionalTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val scenarioRunner: RequestResponseTestScenarioRunner = TestScenarioRunner
    .requestResponseBased()
    .build()

  val sampleSchemas: Map[String, String] = Map(
    "inputSchema" -> """{"type":"object","properties": {"field1": {"type": "integer"}}}""",
    "outputSchema" -> """{"type":"object","properties": {"field1": {"type": "integer"}}, "additionalProperties": false}"""
  )

  test("should not compile if passed additional field when schema restricts this") {
    val runner = scenarioRunner
    def scenario(outputValueExpression: String) = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = sampleSchemas)
      .source("input", "request")
      .emptySink("output", "response", "Raw editor" -> "true", "Value" -> outputValueExpression)

    val redundantField = """invalid"""
    val redundantFieldScenarioCompilationResult = runner.runWithRequests(scenario(s"""{field1: #input.field1, $redundantField: 1}""")) { _ => }
    inside(redundantFieldScenarioCompilationResult) {
      case Invalid(NonEmptyList(CustomNodeError("output", msg, Some("Value")), Nil)) =>
        msg should include (s"The object has redundant fields: Set($redundantField)")
    }

    runner.runWithRequests(scenario("""{field1: #input.field1}""")) { invoker =>
      val inputField = 123
      val response = invoker(HttpRequest(HttpMethods.POST, entity = Map("field1" -> inputField).asJson.spaces2)).rightValue
      response.hcursor.downField("field1").as[Int].rightValue shouldEqual inputField
    }
  }

}
