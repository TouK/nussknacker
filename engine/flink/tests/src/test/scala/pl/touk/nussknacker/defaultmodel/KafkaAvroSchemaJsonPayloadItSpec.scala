package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.schemedkafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.Instant
import java.time.temporal.ChronoUnit

class KafkaAvroSchemaJsonPayloadItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import SampleSchemas._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

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
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${topicConfig.input.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> versionOptionParam(versionOption).spel
      )
      .filter("name-filter", "#input.first == 'Jan'".spel)
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value   -> "".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value     -> s"'${topicConfig.output.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value      -> s"true".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${validationMode.name}'".spel
      )

  test("should read schemed json from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo     = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli
    val topicConfig = createAndRegisterAvroTopicConfig("read-filter-save-json", RecordSchemas)

    val sendResult = sendAsJson(givenMatchingJsonObj, topicConfig.input, timeAgo).futureValue
    logger.info(s"Message sent successful: $sendResult")

    run(avroSchemedJsonPayloadProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val result = kafkaClient.createConsumer().consumeWithJson[Json](topicConfig.output.name).take(1).head

      result.timestamp shouldBe timeAgo
      result.message() shouldEqual parseJson(givenMatchingJsonSchemedObj)
    }
  }

}
