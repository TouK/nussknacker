package pl.touk.nussknacker.engine.lite.components

import io.circe.Json
import org.apache.avro
import org.apache.avro.generic.GenericData
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType
import org.everit.json.schema.Schema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.components.utils.{AvroTestData, JsonTestData}
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util.Optional

class UniversalCrossSourceLiteTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  val avroSchema: avro.Schema = AvroTestData.personSchema
  val jsonSchema: Schema      = JsonTestData.schemaPerson

  private val inputTopic  = "input"
  private val outputTopic = "output"

  private val scenario = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      "my-source",
      KafkaUniversalName,
      topicParamName.value         -> s"'$inputTopic'",
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'"
    )
    .emptySink(
      "my-sink",
      KafkaUniversalName,
      topicParamName.value         -> s"'$outputTopic'",
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'",
      sinkKeyParamName.value       -> "",
      sinkRawEditorParamName.value -> "false",
      "first"                      -> s"#input.first",
      "last"                       -> "#input.last",
      "age"                        -> "#input.age"
    )

  private val scenarioWithRawEditor = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      "my-source",
      KafkaUniversalName,
      topicParamName.value         -> s"'$inputTopic'",
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'"
    )
    .emptySink(
      "my-sink",
      KafkaUniversalName,
      topicParamName.value         -> s"'$outputTopic'",
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'",
      sinkKeyParamName.value       -> "",
      sinkRawEditorParamName.value -> "true",
      sinkValueParamName.value     -> s"#input"
    )

  test("should mix avro schema source and json schema sink") {
    // Given
    val runner   = createRunner
    val schemaId = runner.registerAvroSchema(inputTopic, avroSchema)
    runner.registerJsonSchema(outputTopic, jsonSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val avroPayload = AvroUtils.createRecord(avroSchema, Map("first" -> "John", "last" -> "Doe", "age" -> 21))

    val value = ConfluentUtils.serializeContainerToBytesArray(avroPayload, schemaId)
    val input = new ConsumerRecord(inputTopic, 1, 1, null.asInstanceOf[Array[Byte]], value)

    // When
    val result = runner.runWithRawData(scenario, List(input)).validValue

    // Then
    CirceUtil.decodeJsonUnsafe[Json](result.successes.head.value()) shouldBe CirceUtil.decodeJsonUnsafe[Json](
      inputJsonBytes
    )
  }

  test("should mix json schema source and avro schema sink") {
    // Given
    val runner   = createRunner
    val schemaId = runner.registerJsonSchema(inputTopic, jsonSchema)
    runner.registerAvroSchema(outputTopic, avroSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(
      inputTopic,
      1,
      1,
      ConsumerRecord.NO_TIMESTAMP,
      TimestampType.NO_TIMESTAMP_TYPE,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      null.asInstanceOf[Array[Byte]],
      inputJsonBytes,
      headers,
      Optional.empty[Integer]()
    )

    // When
    val result = runner.runWithRawData(scenario, List(input)).validValue

    // Then
    result.errors shouldBe empty
    val expectedRecord = AvroUtils.createRecord(avroSchema, Map("first" -> "John", "last" -> "Doe", "age" -> 21))
    val resultRecord =
      runner.deserializeAvroData[GenericData.Record](result.successes.head.value(), new RecordHeaders(), isKey = false)
    resultRecord shouldBe expectedRecord
  }

  test("should fail json schema source with avro schema sink when json integer is cast to Long") {
    // Given
    val runner   = createRunner
    val schemaId = runner.registerJsonSchema(inputTopic, jsonSchema)
    runner.registerAvroSchema(outputTopic, avroSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(
      inputTopic,
      1,
      1,
      ConsumerRecord.NO_TIMESTAMP,
      TimestampType.NO_TIMESTAMP_TYPE,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      null.asInstanceOf[Array[Byte]],
      inputJsonBytes,
      headers,
      Optional.empty[Integer]()
    )

    // When
    val result = runner.runWithRawData(scenarioWithRawEditor, List(input))

    // Then
    val CustomNodeError(nodeId, message, _) = result.invalidValue.head
    nodeId shouldBe "my-sink"
    message should include("Incorrect type: path 'age' actual: 'Long' expected: 'Integer'")
  }

  test("should mix json schema source with avro schema sink when json integer is cast to Integer") {
    // Given
    val runner   = createRunner
    val schemaId = runner.registerJsonSchema(inputTopic, JsonTestData.schemaPersonWithLimits)
    runner.registerAvroSchema(outputTopic, avroSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(
      inputTopic,
      1,
      1,
      ConsumerRecord.NO_TIMESTAMP,
      TimestampType.NO_TIMESTAMP_TYPE,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      null.asInstanceOf[Array[Byte]],
      inputJsonBytes,
      headers,
      Optional.empty[Integer]()
    )

    // When
    val result = runner.runWithRawData(scenarioWithRawEditor, List(input)).validValue

    // Then
    result.errors shouldBe empty
    val expectedRecord = AvroUtils.createRecord(avroSchema, Map("first" -> "John", "last" -> "Doe", "age" -> 21))
    val resultRecord =
      runner.deserializeAvroData[GenericData.Record](result.successes.head.value(), new RecordHeaders(), isKey = false)
    resultRecord shouldBe expectedRecord
  }

  private def createRunner = TestScenarioRunner.kafkaLiteBased().build()

}
