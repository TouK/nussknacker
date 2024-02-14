package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.test.PatientScalaFutures

@Slow
class NuKafkaRuntimeDockerAvroTest
    extends AnyFunSuite
    with BaseNuKafkaRuntimeDockerTest
    with Matchers
    with PatientScalaFutures
    with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  private var inputSchemaId: SchemaId = _

  private var outputSchemaId: SchemaId = _

  override val container: Container = {
    kafkaContainer.start()          // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture =
      prepareTestCaseFixture(NuKafkaRuntimeTestSamples.pingPongScenarioName, NuKafkaRuntimeTestSamples.pingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile)
    MultipleContainers(kafkaContainer, schemaRegistryContainer, runtimeContainer)
  }

  private def registerSchemas(): Unit = {
    val parsedAvroSchema = ConfluentUtils.convertToAvroSchema(NuKafkaRuntimeTestSamples.avroPingSchema)
    inputSchemaId =
      SchemaId.fromInt(schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), parsedAvroSchema))
    outputSchemaId = SchemaId.fromInt(
      schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), parsedAvroSchema)
    )
  }

  test("avro ping-pong should work") {
    val valueBytes =
      ConfluentUtils.serializeContainerToBytesArray(NuKafkaRuntimeTestSamples.avroPingRecord, inputSchemaId)
    kafkaClient.sendRawMessage(fixture.inputTopic, "fooKey".getBytes, valueBytes).futureValue
    try {
      val record =
        kafkaClient.createConsumer().consumeWithConsumerRecord(fixture.outputTopic, secondsToWait = 60).take(1).head
      val message = ConfluentUtils
        .deserializeSchemaIdAndData[GenericRecord](record.value(), NuKafkaRuntimeTestSamples.avroPingSchema)
      message shouldBe (outputSchemaId, NuKafkaRuntimeTestSamples.avroPingRecord)
    } finally {
      consumeFirstError shouldBe empty
    }
  }

}
