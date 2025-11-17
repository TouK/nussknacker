package pl.touk.nussknacker.engine.requestresponse

import com.typesafe.config.ConfigFactory
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.RuntimeMode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.components.requestresponse.RequestResponseComponentProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawEditorParamName
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings.{
  InputSchemaProperty,
  OutputSchemaProperty
}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.concurrent.Future

trait BaseScenarioRouteSpec extends AnyFunSuite with ScalatestRouteTest {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  protected def config: RequestResponseConfig

  private val inputSchema  = """{"type" : "object", "properties": {"city": {"type": "string", "default": "Warsaw"}}}"""
  private val outputSchema = """{"type" : "object", "properties": {"place": {"type": "string"}}}"""

  private val scenario = ScenarioBuilder
    .requestResponse("test")
    .additionalFields(
      description = Some("description"),
      properties = Map(InputSchemaProperty -> inputSchema, OutputSchemaProperty -> outputSchema)
    )
    .source("start", "request")
    .emptySink("end", "response", SinkRawEditorParamName.value -> "false".spel, "place" -> "#input.city".spel)

  private val modelData =
    LocalModelData(ConfigFactory.load(), RequestResponseComponentProvider.Components)

  private val scenarioName: ProcessName = scenario.name

  private lazy val interpreter = RequestResponseInterpreter[Future](
    process = scenario,
    processVersion = ProcessVersion.empty.copy(processName = scenarioName),
    nodesDeploymentData = NodesDeploymentData.empty,
    context = LiteEngineRuntimeContextPreparer.noOp,
    modelData = modelData,
    additionalListeners = Nil,
    resultCollector = ProductionServiceInvocationCollector,
    runtimeMode = RuntimeMode.Live,
    securityConfig = config.security
  ).valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))

  def routes: Route = new ScenarioRoute(
    new RequestResponseHttpHandler(interpreter),
    config,
    scenarioName
  ).combinedRoute

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    interpreter.open()
  }

  override protected def afterAll(): Unit = {
    interpreter.close()
    super.afterAll()
  }

}
