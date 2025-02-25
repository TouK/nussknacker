package pl.touk.nussknacker.streaming.embedded

import akka.actor.ActorSystem
import cats.effect.unsafe.IORuntime
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.engine.embedded.EmbeddedDeploymentManagerProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.RequestResponseComponentProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawEditorParamName
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelData}
import pl.touk.nussknacker.test.{AvailablePortFinder, ValidatedValuesDetailedMessage, VeryPatientScalaFutures}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, UriContext, basicRequest}
import sttp.model.StatusCode

class RequestResponseEmbeddedDeploymentManagerTest
    extends AnyFunSuite
    with Matchers
    with VeryPatientScalaFutures
    with ValidatedValuesDetailedMessage {

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  protected def prepareFixture(initiallyDeployedScenarios: List[DeployedScenarioData] = List.empty): FixtureParam = {
    val modelData = LocalModelData(
      ConfigFactory.empty(),
      RequestResponseComponentProvider.Components
    )
    val as: ActorSystem = ActorSystem(getClass.getSimpleName)
    val dependencies = DeploymentManagerDependencies(
      new ProcessingTypeDeployedScenariosProviderStub(initiallyDeployedScenarios),
      new ProcessingTypeActionServiceStub,
      NoOpScenarioActivityManager,
      as.dispatcher,
      IORuntime.global,
      as,
      SttpBackendStub.asynchronousFuture
    )
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val manager = new EmbeddedDeploymentManagerProvider()
      .createDeploymentManager(
        modelData,
        dependencies,
        ConfigFactory
          .empty()
          .withValue("mode", fromAnyRef("request-response"))
          .withValue("http.port", fromAnyRef(port))
          .withValue("http.interface", fromAnyRef("localhost")),
        ScenarioStateCachingConfig.Default.cacheTTL
      )
      .validValue
    FixtureParam(manager, modelData, port)
  }

  sealed case class FixtureParam(deploymentManager: DeploymentManager, modelData: ModelData, port: Int) {

    def deployScenario(scenario: CanonicalProcess): Unit = {
      val version = ProcessVersion.empty.copy(processName = scenario.name)
      deploymentManager
        .processCommand(
          DMRunDeploymentCommand(
            version,
            DeploymentData.empty,
            scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue
    }

  }

  test("Deploys scenario and cancels") {
    val fixture @ FixtureParam(manager, _, port) = prepareFixture()

    val name    = ProcessName("testName")
    val request = basicRequest.post(uri"http://localhost".port(port).withPath("scenario", name.value))

    val inputSchema = """{
        |  "type": "object",
        |  "properties": {
        |    "productId": { "type": "integer" }
        |  }
        |}
        |""".stripMargin
    val outputSchema = """{
        |  "type": "object",
        |  "properties": {
        |    "transformed": { "type": "integer" }
        |  }
        |}
        |""".stripMargin

    val scenario = ScenarioBuilder
      .requestResponse(name.value, name.value)
      .additionalFields(properties =
        Map(
          "inputSchema"  -> inputSchema,
          "outputSchema" -> outputSchema
        )
      )
      .source("source", "request")
      .emptySink(
        "sink",
        "response",
        SinkRawEditorParamName.value -> "false".spel,
        "transformed"                -> "#input.productId".spel
      )

    request.body("""{ productId: 15 }""").send(backend).code shouldBe StatusCode.NotFound

    fixture.deployScenario(scenario)

    eventually {
      manager.getScenarioDeploymentsStatuses(name).futureValue.value.map(_.status) shouldBe List(
        SimpleStateStatus.Running
      )
    }

    request.body("""{ productId: 15 }""").send(backend).body shouldBe Right("""{"transformed":15}""")
    request.body("""Not a correct json""").send(backend).body shouldBe Left(
      """[{"message":"#: expected type: JSONObject, found: String","nodeId":"source"}]"""
    )
    request.body("""{ productId: "11"}""").send(backend).body shouldBe Left(
      """[{"message":"#/productId: expected type: Integer, found: String","nodeId":"source"}]"""
    )

    basicRequest
      .get(uri"http://localhost".port(port).withPath("scenario", name.value, "definition"))
      .send(backend)
      .body
      .toOption
      .get should include("\"openapi\"")

    manager.processCommand(DMCancelScenarioCommand(name, User("a", "b"))).futureValue

    manager.getScenarioDeploymentsStatuses(name).futureValue.value shouldBe List.empty
    request.body("""{ productId: 15 }""").send(backend).code shouldBe StatusCode.NotFound
  }

}
