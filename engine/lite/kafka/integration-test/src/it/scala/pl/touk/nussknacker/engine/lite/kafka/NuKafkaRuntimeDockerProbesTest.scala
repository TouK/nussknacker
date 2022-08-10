package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, ExtremelyPatientScalaFutures}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend, UriContext, asString, basicRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NuKafkaRuntimeDockerProbesTest extends FunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with ExtremelyPatientScalaFutures with LazyLogging with EitherValuesDetailedMessage {

  private val schemaRegistryHostname = "schemaregistry"
  private val schemaRegistryPort = 8081

  private val schemaRegistryContainer = {
    val container = GenericContainer(
      "confluentinc/cp-schema-registry:7.2.1",
      exposedPorts = Seq(schemaRegistryPort),
      env = Map(
        "SCHEMA_REGISTRY_HOST_NAME" -> schemaRegistryHostname,
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> dockerNetworkKafkaBoostrapServer)
    )
    configureNetwork(container, schemaRegistryHostname)
    container
  }

  private def dockerNetworkSchemaRegistryAddress = s"http://$schemaRegistryHostname:$schemaRegistryPort"
  private def mappedSchemaRegistryAddress = s"http://localhost:${schemaRegistryContainer.mappedPort(schemaRegistryPort)}"

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture("probes", NuKafkaRuntimeTestSamples.avroPingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile, checkReady = false, additionalEnvs = Map("SCHEMA_REGISTRY_URL" -> dockerNetworkSchemaRegistryAddress))
    MultipleContainers(kafkaContainer, runtimeContainer)
  }

  private var inputSchemaId: Int = _

  private var outputSchemaId: Int = _

  private def registerSchemas(): Unit = {
    val schemaRegistryClient = new CachedSchemaRegistryClient(mappedSchemaRegistryAddress, 10)
    val parsedAvroSchema = ConfluentUtils.convertToAvroSchema(NuKafkaRuntimeTestSamples.avroPingSchema)
    inputSchemaId = schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), parsedAvroSchema)
    outputSchemaId = schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), parsedAvroSchema)
  }

  private implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()
  private val baseManagementUrl = uri"http://localhost:$runtimeManagementMappedPort"

  test("readiness probe") {
    eventually {
      val readyResult = basicRequest
        .get(baseManagementUrl.path("ready"))
        .response(asString)
        .send().futureValue.body.rightValue
      readyResult shouldBe "OK"
    }
  }

  test("liveness probe") {
    eventually {
      val livenessResult = basicRequest
        .get(baseManagementUrl.path("alive"))
        .response(asString)
        .send().futureValue.body.rightValue
      livenessResult shouldBe "OK"
    }
  }

}