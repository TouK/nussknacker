package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils
import pl.touk.nussknacker.engine.schemedkafka._
import pl.touk.nussknacker.engine.schemedkafka.encode.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit

class GenericItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import KafkaTestUtils._
  import MockSchemaRegistry._
  import spel.Implicits._

  private val secondsToWaitForAvro = 30

  val JsonInTopic: String = "name.json.input"
  val JsonOutTopic: String = "name.json.output"


  private val givenNotMatchingJsonObj =
    """{
      |  "first": "Zenon",
      |  "last": "Nowak",
      |  "nestMap": { "nestedField": "empty" },
      |  "list1": [ {"listField": "full" } ],
      |  "list2": [ 123 ]
      |}""".stripMargin
  private val givenMatchingJsonObj =
    """{
      |  "first": "Jan",
      |  "last": "Kowalski",
      |  "nestMap": { "nestedField": "empty" },
      |  "list1": [ {"listField": "full" } ],
      |  "list2": [ 123 ]
      |}""".stripMargin

  private val givenMatchingJsonSchemedObj =
    """{
      |  "first": "Jan",
      |  "middle": null,
      |  "last": "Kowalski"
      |}""".stripMargin

  private val givenMatchingAvroObjConvertedToV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> null, "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenMatchingAvroObjV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> "Tomek", "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenSecondMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("firstname" -> "Jan"), SecondRecordSchemaV1
  )

  private def jsonSchemedProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption, validationMode: ValidationMode = ValidationMode.strict) =
    ScenarioBuilder
      .streaming("json-schemed-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.input}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.SinkKeyParamName -> "",
        KafkaUniversalComponentTransformer.SinkRawEditorParamName -> "true",
        KafkaUniversalComponentTransformer.SinkValueParamName -> "#input",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaUniversalComponentTransformer.SinkValidationModeParameterName -> s"'${validationMode.name}'"
      )

  private def avroProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption, validationMode: ValidationMode = ValidationMode.strict) =
    ScenarioBuilder
      .streaming("avro-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.input}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.SinkKeyParamName -> "",
        KafkaUniversalComponentTransformer.SinkRawEditorParamName -> "true",
        KafkaUniversalComponentTransformer.SinkValueParamName -> "#input",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaUniversalComponentTransformer.SinkValidationModeParameterName -> s"'${validationMode.name}'"

      )

  private def avroFromScratchProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption) =
    ScenarioBuilder
      .streaming("avro-from-scratch-test")
      .parallelism(1)
      .source(
        "start",
        "kafka-avro",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.input}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .emptySink(
        "end",
        "kafka-avro-raw",
        KafkaUniversalComponentTransformer.SinkKeyParamName -> "",
        KafkaUniversalComponentTransformer.SinkValueParamName -> s"{first: #input.first, last: #input.last}",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaUniversalComponentTransformer.SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> "'1'"
      )

  test("should read avro object from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli

    val topicConfig = createAndRegisterTopicConfig("read-filter-save-avro", RecordSchemas)

    sendAvro(givenNotMatchingAvroObj, topicConfig.input)
    sendAvro(givenMatchingAvroObj, topicConfig.input, timestamp = timeAgo)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val processed = consumeOneRawAvroMessage(topicConfig.output)
      processed.timestamp shouldBe timeAgo
      valueDeserializer.deserialize(topicConfig.output, processed.message()) shouldEqual givenMatchingAvroObjConvertedToV2
    }
  }

  //turn on avroAsJsonSerialization for this test to pass
  ignore("should read schemed json from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli
    val topicConfig = createAndRegisterTopicConfig("read-filter-save-json", RecordSchemas)

    val sendResult = sendAsJson(givenMatchingJsonObj, topicConfig.input, timeAgo).futureValue
    logger.info(s"Message sent successful: $sendResult")

    run(jsonSchemedProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val consumer = kafkaClient.createConsumer()
      val processedMessage = consumer.consume(topicConfig.output, secondsToWaitForAvro).head
      processedMessage.timestamp shouldBe timeAgo
      decodeJsonUnsafe[Json](processedMessage.message()) shouldEqual parseJson(givenMatchingJsonSchemedObj)
    }
  }

  test("should read avro object from kafka and save new one created from scratch") {
    val topicConfig = createAndRegisterTopicConfig("read-save-scratch", RecordSchemaV1)
    sendAvro(givenMatchingAvroObj, topicConfig.input)

    run(avroFromScratchProcess(topicConfig, ExistingSchemaVersion(1))) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual givenMatchingAvroObj
    }
  }

  test("should read avro object in v1 from kafka and deserialize it to v2, filter and save it to kafka in v2") {
    val topicConfig = createAndRegisterTopicConfig("v1.v2.v2", RecordSchemas)
    val result = avroEncoder.encodeRecordOrError(
      Map("first" -> givenMatchingAvroObj.get("first"), "middle" -> null, "last" -> givenMatchingAvroObj.get("last")),
      RecordSchemaV2
    )

    sendAvro(givenMatchingAvroObj, topicConfig.input)

    run(avroProcess(topicConfig, ExistingSchemaVersion(2))) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual result
    }
  }

  test("should read avro object in v2 from kafka and deserialize it to v1, filter and save it to kafka in v2") {
    val topicConfig = createAndRegisterTopicConfig("v2.v1.v1", RecordSchemas)
    sendAvro(givenMatchingAvroObjV2, topicConfig.input)

    val converted = GenericData.get().deepCopy(RecordSchemaV2, givenMatchingAvroObjV2)
    converted.put("middle", null)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual converted
    }
  }

  test("should throw exception when record doesn't match to schema") {
    val topicConfig = createAndRegisterTopicConfig("error-record-matching", RecordSchemas)
    val secondTopicConfig = createAndRegisterTopicConfig("error-second-matching", SecondRecordSchemaV1)

    sendAvro(givenSecondMatchingAvroObj, secondTopicConfig.input)

    assertThrows[Exception] {
      run(avroProcess(topicConfig, ExistingSchemaVersion(1))) {
        val processed = consumeOneAvroMessage(topicConfig.output)
        processed shouldEqual givenSecondMatchingAvroObj
      }
    }
  }

  private def parseJson(str: String) = io.circe.parser.parse(str).right.get

  private def consumeOneRawAvroMessage(topic: String) = {
    val consumer = kafkaClient.createConsumer()
    consumer.consume(topic, secondsToWaitForAvro).head
  }

  private def consumeOneAvroMessage(topic: String) = valueDeserializer.deserialize(topic, consumeOneRawAvroMessage(topic).message())

  private def sendAsJson(jsonString: String, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = jsonString.getBytes(StandardCharsets.UTF_8)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }
}
