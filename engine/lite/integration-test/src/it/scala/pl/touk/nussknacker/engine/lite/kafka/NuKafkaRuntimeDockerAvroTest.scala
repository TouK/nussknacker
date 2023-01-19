package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.test.PatientScalaFutures

class NuKafkaRuntimeDockerAvroTest extends AnyFunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with PatientScalaFutures with LazyLogging {

  private var inputSchemaId: SchemaId = _

  private var outputSchemaId: SchemaId = _

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture(NuKafkaRuntimeTestSamples.pingPongScenarioId, NuKafkaRuntimeTestSamples.pingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile)
    MultipleContainers(kafkaContainer, schemaRegistryContainer, runtimeContainer)
  }

  private def registerSchemas(): Unit = {
    val parsedAvroSchema = ConfluentUtils.convertToAvroSchema(NuKafkaRuntimeTestSamples.avroPingSchema)
    inputSchemaId = SchemaId.fromInt(schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), parsedAvroSchema))
    outputSchemaId = SchemaId.fromInt(schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), parsedAvroSchema))
  }

  test("avro ping-pong should work") {
    val valueBytes = ConfluentUtils.serializeContainerToBytesArray(NuKafkaRuntimeTestSamples.avroPingRecord, inputSchemaId)
    kafkaClient.sendRawMessage(fixture.inputTopic, "fooKey".getBytes, valueBytes).futureValue
    try {
      val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1)
        .map(rec => ConfluentUtils.deserializeSchemaIdAndData[GenericRecord](rec.message(), NuKafkaRuntimeTestSamples.avroPingSchema)).toList
      messages shouldBe List((outputSchemaId, NuKafkaRuntimeTestSamples.avroPingRecord))
    } finally {
      consumeFirstError shouldBe empty
    }
  }

}
