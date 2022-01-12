package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.test.PatientScalaFutures

class NuKafkaRuntimeDockerAvroTest extends FunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with PatientScalaFutures with LazyLogging {

  private val schemaRegistryHostname = "schemaregistry"
  private val schemaRegistryPort = 8081

  private val schemaRegistryContainer = {
    val container = GenericContainer(
      "confluentinc/cp-schema-registry:5.5.0",
      exposedPorts = Seq(schemaRegistryPort),
      env = Map(
        "SCHEMA_REGISTRY_HOST_NAME" -> schemaRegistryHostname,
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> dockerNetworkKafkaBoostrapServer)
    )
    configureNetwork(container, schemaRegistryHostname)
    container
  }

  private var inputSchemaId: Int = _

  private var outputSchemaId: Int = _

  private def mappedSchemaRegistryAddress = s"http://localhost:${schemaRegistryContainer.mappedPort(schemaRegistryPort)}"

  private def dockerNetworkSchemaRegistryAddress = s"http://$schemaRegistryHostname:$schemaRegistryPort"

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture("avro-ping-pong", NuKafkaRuntimeTestSamples.avroPingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile, additionalEnvs = Map("SCHEMA_REGISTRY_URL" -> dockerNetworkSchemaRegistryAddress))
    MultipleContainers(kafkaContainer, schemaRegistryContainer, runtimeContainer)
  }

  private def registerSchemas(): Unit = {
    val schemaRegistryClient = new CachedSchemaRegistryClient(mappedSchemaRegistryAddress, 10)
    val parsedAvroSchema = ConfluentUtils.convertToAvroSchema(NuKafkaRuntimeTestSamples.avroPingSchema)
    inputSchemaId = schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), parsedAvroSchema)
    outputSchemaId = schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), parsedAvroSchema)
  }

  test("avro ping-pong should work") {
    val valueBytes = ConfluentUtils.serializeRecordToBytesArray(NuKafkaRuntimeTestSamples.avroPingRecord, inputSchemaId)
    kafkaClient.sendRawMessage(fixture.inputTopic, "fooKey".getBytes, valueBytes).futureValue
    try {
      val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1)
        .map(rec => ConfluentUtils.deserializeSchemaIdAndRecord(rec.message(), NuKafkaRuntimeTestSamples.avroPingSchema)).toList
      messages shouldBe List((outputSchemaId, NuKafkaRuntimeTestSamples.avroPingRecord))
    } finally {
      consumeFirstError shouldBe empty
    }
  }

}