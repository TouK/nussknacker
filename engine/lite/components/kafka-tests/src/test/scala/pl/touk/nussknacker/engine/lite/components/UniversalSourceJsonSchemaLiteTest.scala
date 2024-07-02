package pl.touk.nussknacker.engine.lite.components

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.io.ByteArrayOutputStream
import java.util.Optional

class UniversalSourceJsonSchemaLiteTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with FunctionalTestMixin {

  import LiteKafkaComponentProvider._
  import io.circe.parser._
  import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val schema = JsonSchemaBuilder.parseSchema("""{
      |  "type": "object",
      |  "properties": {
      |    "first": {
      |      "type": "string"
      |    },
      |    "last": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "type": "integer"
      |    },
      |    "sex": {
      |      "type": "null"
      |    }
      |  }
      |}""".stripMargin)

  private val schemaWithLimits = JsonSchemaBuilder.parseSchema("""{
      |  "type": "object",
      |  "properties": {
      |    "first": {
      |      "type": "string"
      |    },
      |    "last": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "type": "integer",
      |      "minimum": 0,
      |      "maximum": 150
      |    },
      |    "sex": {
      |      "type": "null"
      |    }
      |  }
      |}""".stripMargin)

  private val inputTopic  = TopicName.ForSource("input")
  private val outputTopic = TopicName.ForSink("output")

  private val scenario = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      sourceName,
      KafkaUniversalName,
      topicParamName.value         -> s"'${inputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )
    .emptySink(
      sinkName,
      KafkaUniversalName,
      topicParamName.value         -> s"'${outputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
      sinkKeyParamName.value       -> "".spel,
      sinkRawEditorParamName.value -> "false".spel,
      "first"                      -> s"#input.first".spel,
      "last"                       -> "#input.last".spel,
      "age"                        -> "#input.age".spel,
      "sex"                        -> "#input.sex".spel
    )

  private val scenarioWithRawEditor = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      sourceName,
      KafkaUniversalName,
      topicParamName.value         -> s"'${inputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )
    .emptySink(
      sinkName,
      KafkaUniversalName,
      topicParamName.value         -> s"'${outputTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
      sinkKeyParamName.value       -> "".spel,
      sinkRawEditorParamName.value -> "true".spel,
      sinkValueParamName.value     -> s"#input".spel
    )

  test("should read data on json schema based universal source when schemaId in header") {
    // Given

    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, schema)
    runner.registerJsonSchema(outputTopic.toUnspecialized, schema)

    // When
    val record =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21,
        |  "sex": null
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
      record,
      headers,
      Optional.empty[Integer]()
    )

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result                                               = runner.runWithRawData(scenario, list).validValue

    // Then
    parse(new String(input.value())) shouldBe parse(new String(result.successes.head.value()))
  }

  test("should read data on json schema based universal source when schemaId in wire-format") {
    // Given
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, schema)
    runner.registerJsonSchema(outputTopic.toUnspecialized, schema)

    // When
    val stringRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21,
        |  "sex": null
        |}""".stripMargin
    val record = stringRecord.getBytes()

    val recordWithWireFormatSchemaId = new ByteArrayOutputStream
    ConfluentUtils.writeSchemaId(schemaId, recordWithWireFormatSchemaId)
    recordWithWireFormatSchemaId.write(record)

    val input =
      new ConsumerRecord(
        inputTopic.name,
        1,
        1,
        null.asInstanceOf[Array[Byte]],
        recordWithWireFormatSchemaId.toByteArray
      )

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result                                               = runner.runWithRawData(scenario, list).validValue

    // Then
    parse(stringRecord) shouldBe parse(new String(result.successes.head.value()))
  }

  test("should raise compilation error in the case when written type is widen than a type in the sink") {
    // Given
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, schema)
    runner.registerJsonSchema(outputTopic.toUnspecialized, schemaWithLimits)

    // When
    val stringRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21,
        |  "sex": null
        |}""".stripMargin
    val record = stringRecord.getBytes()

    val recordWithWireFormatSchemaId = new ByteArrayOutputStream
    ConfluentUtils.writeSchemaId(schemaId, recordWithWireFormatSchemaId)
    recordWithWireFormatSchemaId.write(record)

    val input =
      new ConsumerRecord(
        inputTopic.name,
        1,
        1,
        null.asInstanceOf[Array[Byte]],
        recordWithWireFormatSchemaId.toByteArray
      )

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result                                               = runner.runWithRawData(scenarioWithRawEditor, list)

    // Then
    result shouldBe invalidTypes("path 'age' actual: 'Long' expected: 'Integer'")
  }

  test("should read/write data on json schema based universal source") {
    // Given
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, schemaWithLimits)
    runner.registerJsonSchema(outputTopic.toUnspecialized, schemaWithLimits)

    // When
    val stringRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21,
        |  "sex": null
        |}""".stripMargin
    val record = stringRecord.getBytes()

    val recordWithWireFormatSchemaId = new ByteArrayOutputStream
    ConfluentUtils.writeSchemaId(schemaId, recordWithWireFormatSchemaId)
    recordWithWireFormatSchemaId.write(record)

    val input =
      new ConsumerRecord(
        inputTopic.name,
        1,
        1,
        null.asInstanceOf[Array[Byte]],
        recordWithWireFormatSchemaId.toByteArray
      )

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result = runner.runWithRawData(scenarioWithRawEditor, list).validValue

    // Then
    parse(stringRecord) shouldBe parse(new String(result.successes.head.value()))
  }

  test("should read/write data on json schema when type in sink is wider than in source") {
    // Given
    val schemaId = runner.registerJsonSchema(inputTopic.toUnspecialized, schemaWithLimits)
    runner.registerJsonSchema(outputTopic.toUnspecialized, schema)

    // When
    val stringRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21,
        |  "sex": null
        |}""".stripMargin
    val record = stringRecord.getBytes()

    val recordWithWireFormatSchemaId = new ByteArrayOutputStream
    ConfluentUtils.writeSchemaId(schemaId, recordWithWireFormatSchemaId)
    recordWithWireFormatSchemaId.write(record)

    val input =
      new ConsumerRecord(
        inputTopic.name,
        1,
        1,
        null.asInstanceOf[Array[Byte]],
        recordWithWireFormatSchemaId.toByteArray
      )

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result = runner.runWithRawData(scenarioWithRawEditor, list).validValue

    // Then
    parse(stringRecord) shouldBe parse(new String(result.successes.head.value()))
  }

}
