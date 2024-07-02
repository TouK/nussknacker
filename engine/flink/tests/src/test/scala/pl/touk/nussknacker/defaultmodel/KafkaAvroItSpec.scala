package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.schemedkafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.Instant
import java.time.temporal.ChronoUnit

class KafkaAvroItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import SampleSchemas._

  private val givenMatchingAvroObjConvertedToV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> null, "last" -> "Kowalski"),
    RecordSchemaV2
  )

  private val givenMatchingAvroObjV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> "Tomek", "last" -> "Kowalski"),
    RecordSchemaV2
  )

  private val givenSecondMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("firstname" -> "Jan"),
    SecondRecordSchemaV1
  )

  private def avroProcess(
      topicConfig: TopicConfig,
      versionOption: SchemaVersionOption,
      validationMode: ValidationMode = ValidationMode.strict
  ) =
    ScenarioBuilder
      .streaming("avro-test")
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
        KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value     -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${topicConfig.output.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${validationMode.name}'".spel
      )

  private def avroFromScratchProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption) =
    ScenarioBuilder
      .streaming("avro-from-scratch-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${topicConfig.input.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> versionOptionParam(versionOption).spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value -> s"{first: #input.first, last: #input.last}".spel,
        KafkaUniversalComponentTransformer.topicParamName.value     -> s"'${topicConfig.output.name}'".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.strict.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value      -> "'1'".spel
      )

  test("should read avro object from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.HOURS).toEpochMilli

    val topicConfig = createAndRegisterAvroTopicConfig("read-filter-save-avro", RecordSchemas)

    sendAvro(givenNotMatchingAvroObj, topicConfig.input)
    sendAvro(givenMatchingAvroObj, topicConfig.input, timestamp = timeAgo)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val processed = kafkaClient.createConsumer().consumeWithConsumerRecord(topicConfig.output.name).take(1).head
      processed.timestamp shouldBe timeAgo
      valueDeserializer.deserialize(
        topicConfig.output.name,
        processed.value()
      ) shouldEqual givenMatchingAvroObjConvertedToV2
    }
  }

  test("should read avro object from kafka and save new one created from scratch") {
    val topicConfig = createAndRegisterAvroTopicConfig("read-save-scratch", RecordSchemaV1)
    sendAvro(givenMatchingAvroObj, topicConfig.input)

    run(avroFromScratchProcess(topicConfig, ExistingSchemaVersion(1))) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual givenMatchingAvroObj
    }
  }

  test("should read avro object in v1 from kafka and deserialize it to v2, filter and save it to kafka in v2") {
    val topicConfig = createAndRegisterAvroTopicConfig("v1.v2.v2", RecordSchemas)
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
    val topicConfig = createAndRegisterAvroTopicConfig("v2.v1.v1", RecordSchemas)
    sendAvro(givenMatchingAvroObjV2, topicConfig.input)

    val converted = GenericData.get().deepCopy(RecordSchemaV2, givenMatchingAvroObjV2)
    converted.put("middle", null)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.lax)) {
      val processed = consumeOneAvroMessage(topicConfig.output)
      processed shouldEqual converted
    }
  }

  test("should throw exception when record doesn't match to schema") {
    val topicConfig       = createAndRegisterAvroTopicConfig("error-record-matching", RecordSchemas)
    val secondTopicConfig = createAndRegisterAvroTopicConfig("error-second-matching", SecondRecordSchemaV1)

    sendAvro(givenSecondMatchingAvroObj, secondTopicConfig.input)

    assertThrows[Exception] {
      run(avroProcess(topicConfig, ExistingSchemaVersion(1))) {
        val processed = consumeOneAvroMessage(topicConfig.output)
        processed shouldEqual givenSecondMatchingAvroObj
      }
    }
  }

  private def consumeOneAvroMessage(topic: TopicName.ForSink) =
    valueDeserializer.deserialize(
      topic.name,
      kafkaClient.createConsumer().consumeWithConsumerRecord(topic.name).take(1).head.value()
    )

}
