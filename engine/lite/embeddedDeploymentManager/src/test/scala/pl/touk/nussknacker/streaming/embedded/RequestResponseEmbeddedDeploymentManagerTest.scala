package pl.touk.nussknacker.streaming.embedded

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, DeploymentManager, ProcessingTypeDeploymentServiceStub}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.engine.embedded.RequestResponseEmbeddedDeploymentManagerProvider
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink.SinkRawEditorParamName
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{AvailablePortFinder, PatientScalaFutures, VeryPatientScalaFutures}
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend, UriContext, basicRequest}
import sttp.model.StatusCode

import scala.concurrent.Future

class RequestResponseEmbeddedDeploymentManagerTest extends AnyFunSuite with Matchers with VeryPatientScalaFutures {

  private implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  protected def prepareFixture(initiallyDeployedScenarios: List[DeployedScenarioData] = List.empty): FixtureParam = {

    val modelData = LocalModelData(ConfigFactory.empty()
      .withValue("components.kafka.disabled", fromAnyRef(true))
      .withValue("components.mockKafka.disabled", fromAnyRef(true)), new EmptyProcessConfigCreator)
    implicit val deploymentService: ProcessingTypeDeploymentServiceStub = new ProcessingTypeDeploymentServiceStub(initiallyDeployedScenarios)
    implicit val as: ActorSystem = ActorSystem(getClass.getSimpleName)
    implicit val dummyBackend: SttpBackend[Future, Nothing, NothingT] = null
    import as.dispatcher
    val port = AvailablePortFinder.findAvailablePorts(1).head
    val manager = new RequestResponseEmbeddedDeploymentManagerProvider().createDeploymentManager(modelData,
      ConfigFactory.empty().withValue("http.port", fromAnyRef(port)).withValue("http.interface", fromAnyRef("localhost")))
    FixtureParam(manager, modelData, port)
  }

  case class FixtureParam(deploymentManager: DeploymentManager, modelData: ModelData, port: Int) {
    def deployScenario(scenario: EspProcess): Unit = {
      val version = ProcessVersion.empty.copy(processName = ProcessName(scenario.id))
      deploymentManager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue
    }
  }

  test("Deploys scenario and cancels") {
    val fixture@FixtureParam(manager, _, port) = prepareFixture()

    val name = ProcessName("testName")
    val request = basicRequest.post(uri"http://localhost".port(port).path("scenario", name.value))

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
      .requestResponse(name.value)
      .additionalFields(properties = Map(
        "inputSchema" -> inputSchema,
        "outputSchema" -> outputSchema
      ))
      .source("source", "request")
      .emptySink("sink", "response", SinkRawEditorParamName -> "false", "transformed" -> "#input.productId")

    request.body("""{ productId: 15 }""").send().code shouldBe StatusCode.NotFound
    
    fixture.deployScenario(scenario)

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }

    request.body("""{ productId: 15 }""").send().body shouldBe Right("""{"transformed":15}""")
    request.body("""Not a correct json""").send().body shouldBe Left("""[{"message":"#: expected type: JSONObject, found: String","nodeId":"source"}]""")
    request.body("""{ productId: "11"}""").send().body shouldBe Left("""[{"message":"#/productId: expected type: Integer, found: String","nodeId":"source"}]""")

    manager.cancel(name, User("a", "b")).futureValue

    manager.findJobStatus(name).futureValue shouldBe None
    request.body("""{ productId: 15 }""").send().code shouldBe StatusCode.NotFound
  }




}
