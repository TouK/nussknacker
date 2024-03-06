package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.schemedkafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.Instant
import java.time.temporal.ChronoUnit

class KafkaAvroSchemaJsonPayloadItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import SampleSchemas._
  import spel.Implicits._

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

  private def avroSchemedJsonPayloadProcess(
      topicConfig: TopicConfig,
      versionOption: SchemaVersionOption,
      validationMode: ValidationMode = ValidationMode.strict
  ) =
    ScenarioBuilder
      .streaming("json-schemed-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.TopicParamName         -> s"'${topicConfig.input}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.SinkKeyParamName       -> "",
        KafkaUniversalComponentTransformer.SinkValueParamName     -> "#input",
        KafkaUniversalComponentTransformer.TopicParamName         -> s"'${topicConfig.output}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaUniversalComponentTransformer.SinkRawEditorParamName -> s"true",
        KafkaUniversalComponentTransformer.SinkValidationModeParameterName -> s"'${validationMode.name}'"
      )

  test("should read schemed json from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo     = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli
    val topicConfig = createAndRegisterAvroTopicConfig("read-filter-save-json", RecordSchemas)

    val sendResult = sendAsJson(givenMatchingJsonObj, topicConfig.input, timeAgo).futureValue
    logger.info(s"Message sent successful: $sendResult")

    run(avroSchemedJsonPayloadProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val result = kafkaClient.createConsumer().consumeWithJson[Json](topicConfig.output).take(1).head

      result.timestamp shouldBe timeAgo
      result.message() shouldEqual parseJson(givenMatchingJsonSchemedObj)
    }
  }

}
