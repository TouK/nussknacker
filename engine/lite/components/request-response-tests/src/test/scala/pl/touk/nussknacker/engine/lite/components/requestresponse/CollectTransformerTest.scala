package pl.touk.nussknacker.engine.lite.components.requestresponse

import cats.data.{NonEmptyList, ValidatedNel}
import io.circe.Json
import org.apache.pekko.http.javadsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{HttpHeader, HttpMethods, HttpRequest}
import org.scalatest.Inside.inside
import org.scalatest.LoneElement._
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke, NodeId, Service, TraceId}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.build.{ProcessGraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawEditorParamName
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{
  InputSchemaProperty,
  OutputSchemaProperty
}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, ValidatedValuesDetailedMessage}

import scala.concurrent.Future

class CollectTransformerTest
    extends AnyFunSuite
    with Matchers
    with EitherValuesDetailedMessage
    with OptionValues
    with TableDrivenPropertyChecks
    with ValidatedValuesDetailedMessage {

  private val inputSchema =
    """
      |{
      |  "type": "array",
      |  "items": {
      |    "type": "number"
      |  }
      |}
      |""".stripMargin

  private val outputSchema =
    """
      |{
      |  "type": "array",
      |  "items": {
      |    "type": "string"
      |  }
      |}
      |""".stripMargin

  test("should collect elements after for-each") {
    val scenario = scenarioBuilderWithSchemas
      .customNode("for-each", "outForEach", "for-each", "Elements" -> "#input".spel)
      .buildSimpleVariable("someVar", "ourVar", "'x = ' + (#outForEach.intValue() * 2)".spel)
      .customNode("collect", "outCollector", "collect", "Input expression" -> "#ourVar".spel)
      .emptySink("response", "response", SinkRawEditorParamName.value -> "true".spel, "Value" -> "#outCollector".spel)
    val requestElements = (0 to 3).toList

    val responseElements = runScenarioAndExtractResponseElements(scenario, requestElements, traceId = None)
    val expectedElements = requestElements.map(s => s"x = ${s * 2}")
    responseElements should contain theSameElementsInOrderAs expectedElements
  }

  test("should properly pass context id after collect") {
    val traceId = TraceId.generate()
    val scenario = scenarioBuilderWithSchemas
      .customNode("for-each", "outForEach", "for-each", "Elements" -> "#input".spel)
      .customNode("collect", "outCollector", "collect", "Input expression" -> "#outForEach".spel)
      .enricher("correlationId", "correlationId", CorrelationEnricher.ComponentName)
      .emptySink(
        "response",
        "response",
        SinkRawEditorParamName.value -> "true".spel,
        "Value"                      -> "{#correlationId}".spel
      )

    val requestElements  = (0 to 3).toList
    val responseElements = runScenarioAndExtractResponseElements(scenario, requestElements, traceId = Some(traceId))
    responseElements shouldBe List(traceId.value)
  }

  test("should clear context variables") {
    val nodeIdWithError = "use previous ctx variable"
    val scenario = scenarioBuilderWithSchemas
      .customNode("for-each", "outForEach", "for-each", "Elements" -> "#input".spel)
      .buildSimpleVariable("this variable should disappear", "previousCtxVar", "'value'".spel)
      .customNode("collect", "outCollector", "collect", "Input expression" -> "#outForEach".spel)
      .buildSimpleVariable(nodeIdWithError, "newCtxVar", "#previousCtxVar".spel)
      .emptySink("response", "response", SinkRawEditorParamName.value -> "true".spel, "Value" -> "{'abc'}".spel)

    val compilationError = runScenario(scenario, List(1), traceId = None).invalidValue.toList.loneElement

    inside(compilationError) {
      case ExpressionParserCompilationError(
            "Unresolved reference 'previousCtxVar'",
            NodeId(`nodeIdWithError`),
            _,
            _,
            _
          ) =>
    }
  }

  test("should collect elements after nested for-each") {
    val scenario = scenarioBuilderWithSchemas
      .customNode("for-each1", "outForEach1", "for-each", "Elements" -> "#input".spel)
      .customNode("for-each2", "outForEach2", "for-each", "Elements" -> "#input".spel)
      .buildSimpleVariable("someVar", "outVar", "'i = ' + #outForEach1 + ', j = ' + #outForEach2".spel)
      .customNode("collect", "outCollector", "collect", "Input expression" -> "#outVar".spel)
      .emptySink("response", "response", SinkRawEditorParamName.value -> "true".spel, "Value" -> "#outCollector".spel)
    val requestElements = (0 to 3).toList

    val responseElements = runScenarioAndExtractResponseElements(scenario, requestElements, traceId = None)
    val expectedElements = for {
      i <- requestElements
      j <- requestElements
    } yield s"i = $i, j = $j"
    responseElements should contain theSameElementsInOrderAs expectedElements
  }

  private def scenarioBuilderWithSchemas: ProcessGraphBuilder = {
    ScenarioBuilder
      .requestResponse("proc")
      .additionalFields(properties =
        Map(
          InputSchemaProperty  -> inputSchema,
          OutputSchemaProperty -> outputSchema
        )
      )
      .source("request", "request")
  }

  private def runScenarioAndExtractResponseElements(
      scenario: CanonicalProcess,
      requestElements: Seq[Int],
      traceId: Option[TraceId],
  ): Seq[String] = {
    val runResult        = runScenario(scenario, requestElements, traceId)
    val responseJson     = runResult.validValue.rightValue
    val responseElements = responseJson.asArray.value.map(_.asString.value)
    responseElements
  }

  private def runScenario(
      scenario: CanonicalProcess,
      requestElements: Seq[Int],
      traceId: Option[TraceId],
  ): ValidatedNel[ProcessCompilationError, Either[NonEmptyList[ErrorType], Json]] = {
    TestScenarioRunner
      .requestResponseBased()
      .withExtraComponents(
        List(ComponentDefinition(CorrelationEnricher.ComponentName, CorrelationEnricher))
      )
      .build()
      .runWithRequests(scenario) { invoker =>
        invoker(
          HttpRequest(
            HttpMethods.POST,
            entity = Json.arr(requestElements.map(Json.fromInt): _*).noSpaces,
            headers = traceId
              .map(tr => Seq(RawHeader.create(BaseLiteSource.DefaultTraceIdHeader, tr.value)))
              .getOrElse(Seq.empty)
          )
        )
      }
  }

  case object CorrelationEnricher extends Service {

    val ComponentName = "correlationEnricher"

    @MethodToInvoke
    def invoke()(implicit context: Context): Future[String] = {
      Future.successful(context.traceId.map(_.value).getOrElse(throw new IllegalArgumentException("Missing traceId")))
    }

  }

}
