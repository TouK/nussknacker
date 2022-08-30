package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
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

class KafkaAvroSchemaJsonPayloadItSpec extends  FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import KafkaTestUtils._
  import MockSchemaRegistry._
  import spel.Implicits._

  private val secondsToWaitForAvro = 30

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

  override val avroAsJsonSerialization: Boolean = true

  private def avroSchemedJsonPayloadProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption, validationMode: ValidationMode = ValidationMode.strict) =
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
        KafkaUniversalComponentTransformer.SinkValueParamName -> "#input",
        KafkaUniversalComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaUniversalComponentTransformer.SinkRawEditorParamName -> s"true",
        KafkaUniversalComponentTransformer.SinkValidationModeParameterName -> s"'${validationMode.name}'"
      )

  test("should read schemed json from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli
    val topicConfig = createAndRegisterAvroTopicConfig("read-filter-save-json", RecordSchemas)

    val sendResult = sendAsJson(givenMatchingJsonObj, topicConfig.input, timeAgo).futureValue
    logger.info(s"Message sent successful: $sendResult")

    run(avroSchemedJsonPayloadProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val consumer = kafkaClient.createConsumer()
      val processedMessage = consumer.consume(topicConfig.output, secondsToWaitForAvro).head
      processedMessage.timestamp shouldBe timeAgo
      decodeJsonUnsafe[Json](processedMessage.message()) shouldEqual parseJson(givenMatchingJsonSchemedObj)
    }
  }

  private def parseJson(str: String) = io.circe.parser.parse(str).right.get

  private def sendAsJson(jsonString: String, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = jsonString.getBytes(StandardCharsets.UTF_8)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }
}
