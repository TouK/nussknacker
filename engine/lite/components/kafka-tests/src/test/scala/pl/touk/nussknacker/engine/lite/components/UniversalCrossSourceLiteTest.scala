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
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.lite.components.utils.{AvroTestData, JsonTestData}
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util.Optional

class UniversalCrossSourceLiteTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._

  val avroSchema: avro.Schema = AvroTestData.personSchema
  val jsonSchema: Schema      = JsonTestData.schemaPerson

  private val inputTopic  = TopicName.ForSource("input")
  private val outputTopic = TopicName.ForSink("output")

  private val scenario = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      "my-source",
      KafkaUniversalName,
      topicParamName.value         -> s"'${inputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )
    .emptySink(
      "my-sink",
      KafkaUniversalName,
      topicParamName.value         -> s"'${outputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
      sinkKeyParamName.value       -> "".spel,
      sinkRawEditorParamName.value -> "false".spel,
      "first"                      -> s"#input.first".spel,
      "last"                       -> "#input.last".spel,
      "age"                        -> "#input.age".spel
    )

  private val scenarioWithRawEditor = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      "my-source",
      KafkaUniversalName,
      topicParamName.value         -> s"'${inputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )
    .emptySink(
      "my-sink",
      KafkaUniversalName,
      topicParamName.value         -> s"'${outputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
      sinkKeyParamName.value       -> "".spel,
      sinkRawEditorParamName.value -> "true".spel,
      sinkValueParamName.value     -> s"#input".spel
    )

  test("should mix avro schema source and json schema sink") {
    // Given
    val runner   = createRunner
    val schemaId = runner.registerAvroSchema(inputTopic.toUnspecialized, avroSchema)
    runner.registerJsonSchema(outputTopic.toUnspecialized, jsonSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val avroPayload = AvroUtils.createRecord(avroSchema, Map("first" -> "John", "last" -> "Doe", "age" -> 21))

    val value = ConfluentUtils.serializeContainerToBytesArray(avroPayload, schemaId)
    val input = new ConsumerRecord(inputTopic.name, 1, 1, null.asInstanceOf[Array[Byte]], value)

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
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, jsonSchema)
    runner.registerAvroSchema(outputTopic.toUnspecialized, avroSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(
      inputTopic.name,
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
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, jsonSchema)
    runner.registerAvroSchema(outputTopic.toUnspecialized, avroSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(
      inputTopic.name,
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
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, JsonTestData.schemaPersonWithLimits)
    runner.registerAvroSchema(outputTopic.toUnspecialized, avroSchema)

    val inputJsonBytes =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(
      inputTopic.name,
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
