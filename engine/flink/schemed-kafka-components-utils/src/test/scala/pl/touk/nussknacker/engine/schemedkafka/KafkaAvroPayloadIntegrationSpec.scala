package pl.touk.nussknacker.engine.schemedkafka

import io.circe.generic.JsonCodec
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{Assertion, BeforeAndAfter}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumer
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroPayloadIntegrationSpec.sinkForInputMetaResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{
  MockConfluentSchemaRegistryClientBuilder,
  MockSchemaRegistryClient
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.testing.LocalModelData
import scala.jdk.CollectionConverters._

import java.nio.charset.StandardCharsets

class KafkaAvroPayloadIntegrationSpec extends KafkaAvroSpecMixin with BeforeAndAfter {

  import KafkaAvroIntegrationMockSchemaRegistry._
  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator(
    sinkForInputMetaResultsHolder
  ) {
    override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
      MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

  protected val paymentSchemas: List[Schema]  = List(PaymentV1.schema, PaymentV2.schema)
  protected val payment2Schemas: List[Schema] = List(PaymentV1.schema, PaymentV2.schema, PaymentNotCompatible.schema)

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    modelData = LocalModelData(config, List.empty, configCreator = creator)
  }

  before {
    KafkaAvroPayloadIntegrationSpec.sinkForInputMetaResultsHolder.clear()
  }

  test("should read event in the same version as source requires and save it in the same version") {
    val topicConfig = createAndRegisterTopicConfig("simple", PaymentV1.schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(
      process,
      topicConfig,
      PaymentV1.record,
      PaymentV1.record, {
        // Here process uses value-only source with metadata, the content of event's key is treated as a string.
        verifyInputMeta(null, topicConfig.input, 0, 0L)
      }
    )
  }

  test("should read primitive event and save it in the same format") {
    val topicConfig = createAndRegisterTopicConfig("simple.primitive", Schema.create(Schema.Type.STRING))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, "fooBar", "fooBar")
  }

  test("should handle invalid expression type for topic") {
    val topicConfig = createAndRegisterTopicConfig("simple.bad-expression-type", Schema.create(Schema.Type.STRING))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, "#input")
    val process     = createAvroProcess(sourceParam, sinkParam, sourceTopicParamValue = _ => s"'invalid-topic'")

    val message = intercept[Exception] {
      runAndVerifyResultSingleEvent(process, topicConfig, "fooBar", "fooBar")
    }.getMessage

    message should include("InvalidPropertyFixedValue(ParameterName(Topic),None,'invalid-topic',")
  }

  test("should handle null value for mandatory parameter") {
    val topicConfig = createAndRegisterTopicConfig("simple.empty-mandatory-field", Schema.create(Schema.Type.STRING))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, "#input")
    val process     = createAvroProcess(sourceParam, sinkParam, sourceTopicParamValue = _ => s"")

    val message = intercept[Exception] {
      runAndVerifyResultSingleEvent(process, topicConfig, "fooBar", "fooBar")
    }.getMessage

    message should include(
      "EmptyMandatoryParameter(This field is mandatory and can not be empty,Please fill field for this parameter,ParameterName(Topic),start"
    )
  }

  test("should read newer compatible event then source requires and save it in older compatible version") {
    val topicConfig = createAndRegisterTopicConfig("newer-older-older", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV2.record, PaymentV1.record)
  }

  test("should read older compatible event then source requires and save it in newer compatible version") {
    val topicConfig = createAndRegisterTopicConfig("older-newer-newer", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(2), "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV2.record)
  }

  test("should read event in the same version as source requires and save it in newer compatible version") {
    val topicConfig = createAndRegisterTopicConfig("older-older-newer", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam =
      UniversalSinkParam(topicConfig, ExistingSchemaVersion(2), "#input", validationMode = Some(ValidationMode.lax))
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV2.record)
  }

  test("should read older compatible event then source requires and save it in older compatible version") {
    val topicConfig = createAndRegisterTopicConfig("older-newer-older", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam =
      UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input", validationMode = Some(ValidationMode.lax))
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV1.record)
  }

  test("should read older compatible event with source and save it in latest compatible version") {
    val topicConfig = createAndRegisterTopicConfig("older-latest-latest", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV2.record)
  }

  test("should read older compatible event then source requires, filter and save it in older compatible version") {
    val topicConfig = createAndRegisterTopicConfig("older-newer-filter-older", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam =
      UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input", validationMode = Some(ValidationMode.lax))
    val filterParam = Some("#input.cnt == 0")
    val process     = createAvroProcess(sourceParam, sinkParam, filterParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV1.record)
  }

  test("should read compatible events with source, filter and save only one") {
    val topicConfig = createAndRegisterTopicConfig("two-source-filter-one", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(2), "#input")
    val filterParam = Some("#input.cnt == 1")
    val process     = createAvroProcess(sourceParam, sinkParam, filterParam)
    val events      = List(PaymentV1.record, PaymentV2.recordWithData)

    runAndVerifyResult(process, topicConfig, events, PaymentV2.recordWithData)
  }

  test("should read newer (back-compatible) newer event with source and save it in older compatible version") {
    val topicConfig = createAndRegisterTopicConfig("bc-older-older", payment2Schemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam =
      UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input", validationMode = Some(ValidationMode.lax))
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentNotCompatible.record, PaymentV1.record)
  }

  test("should read older compatible event with source and save it in latest compatible version with map output") {
    val topicConfig = createAndRegisterTopicConfig("older-output-with-map", List(PaymentV1.schema, PaymentV2.schema))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, PaymentV2.jsonMap)
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV2.record)
  }

  test("should read newer compatible event with source and save it in older compatible version with map output") {
    val topicConfig = createAndRegisterTopicConfig("newer-output-with-map", List(PaymentV1.schema, PaymentV2.schema))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), PaymentV1.jsonMap)
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV2.record, PaymentV1.record)
  }

  test("should rise exception when we provide wrong data map for #Avro helper output") {
    val topicConfig = createAndRegisterTopicConfig("bad-data-with-helper", List(PaymentV1.schema, PaymentV2.schema))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), """{id: "bad"}""")
    val process     = createAvroProcess(sourceParam, sinkParam)

    assertThrowsWithParent[Exception] {
      runAndVerifyResultSingleEvent(process, topicConfig, PaymentV2.record, PaymentV1.record)
    }
  }

  test("should handle exception when saving runtime incompatible event") {
    val topicConfig = createAndRegisterTopicConfig("runtime-incompatible", Address.schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam(
      topicConfig,
      ExistingSchemaVersion(1),
      "{city: #input.city, street: #input.city == 'Warsaw' ? #input.street : null}"
    )
    val events  = List(Address.encode(Address.exampleData + ("city" -> "Ochota")), Address.record)
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(
      process,
      topicConfig,
      events,
      Address.record, {
        eventually {
          RecordingExceptionConsumer.exceptionsFor(runId) should have size 1
          val espExceptionInfo = RecordingExceptionConsumer.exceptionsFor(runId).head

          espExceptionInfo.nodeComponentInfo shouldBe Some(
            NodeComponentInfo("end", ComponentType.Sink, "flinkKafkaAvroSink")
          )
          espExceptionInfo.throwable shouldBe a[NonTransientException]
          val cause = espExceptionInfo.throwable.asInstanceOf[NonTransientException].cause
          cause shouldBe a[AvroRuntimeException]
          cause.getMessage should include("Not expected null for field: Some(street)")
        }
      }
    )
  }

  test("should throw exception when try to filter by missing field") {
    val topicConfig = createAndRegisterTopicConfig("try-filter-by-missing-field", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val filterParam = Some("#input.cnt == 1")
    val events      = List(PaymentV1.record, PaymentV2.record)
    val process     = createAvroProcess(sourceParam, sinkParam, filterParam)

    assertThrowsWithParent[Exception] {
      runAndVerifyResult(process, topicConfig, events, PaymentV2.recordWithData)
    }
  }

  test("should handle convert not compatible event") {
    val topicConfig = createAndRegisterTopicConfig("try-to-convert-not-compatible", payment2Schemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(3))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(3), "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)
    runAndVerifyResult(
      process,
      topicConfig,
      List(PaymentV2.recordWithData, PaymentNotCompatible.recordWithData),
      PaymentNotCompatible.recordWithData, {
        eventually {
          RecordingExceptionConsumer.exceptionsFor(runId) should have size 1
        }
      }
    )
  }

  test("should pass timestamp from flink to kafka") {
    val topicConfig = createAndRegisterTopicConfig("timestamp-flink-kafka", LongFieldV1.schema)
    // Can't be too long ago, otherwise retention could delete it
    val timeToSetInProcess = System.currentTimeMillis() - 600000L

    val process = ScenarioBuilder
      .streaming("avro-test-timestamp-flink-kafka")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        topicParamName.value         -> s"'${topicConfig.input.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .customNode(
        "transform",
        "extractedTimestamp",
        "extractAndTransformTimestamp",
        "timestampToSet" -> (timeToSetInProcess.toString + "L").spel
      )
      .emptySink(
        "end",
        "kafka",
        topicParamName.value              -> s"'${topicConfig.output.name}'".spel,
        schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value            -> "".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
        sinkValueParamName.value          -> s"{field: #extractedTimestamp}".spel
      )

    pushMessage(LongFieldV1.record, topicConfig.input)
    kafkaClient.createTopic(topicConfig.output.name)
    run(process) {
      val message = kafkaClient.createConsumer().consumeWithConsumerRecord(topicConfig.output.name).take(1).head
      message.timestamp() shouldBe timeToSetInProcess
      message.timestampType() shouldBe TimestampType.CREATE_TIME
    }
  }

  test("should pass timestamp from kafka to flink") {
    val topicConfig = createAndRegisterTopicConfig("timestamp-kafka-flink", LongFieldV1.schema)

    val process = ScenarioBuilder
      .streaming("avro-test-timestamp-kafka-flink")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        topicParamName.value         -> s"'${topicConfig.input.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "10000".spel)
      .emptySink(
        "end",
        "kafka",
        topicParamName.value              -> s"'${topicConfig.output.name}'".spel,
        schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value            -> "".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
        sinkValueParamName.value          -> s"{field: #extractedTimestamp}".spel
      )

    // Can't be too long ago, otherwise retention could delete it
    val timePassedThroughKafka = System.currentTimeMillis() - 120000L
    pushMessage(LongFieldV1.encodeData(-1000L), topicConfig.input, timestamp = timePassedThroughKafka)
    kafkaClient.createTopic(topicConfig.output.name)
    run(process) {
      consumeAndVerifyMessages(topicConfig.output, List(LongFieldV1.encodeData(timePassedThroughKafka)))
    }
  }

  test("should accept logical types in generic record") {
    val topicConfig = createAndRegisterTopicConfig("logical-fields-generic", List(PaymentDate.schema))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, "#input")
    val process = createAvroProcess(
      sourceParam,
      sinkParam,
      Some(
        s"#input.dateTime.toEpochMilli == ${PaymentDate.instant.toEpochMilli}L AND " +
          s"#input.date.year == ${PaymentDate.date.getYear} AND #input.date.monthValue == ${PaymentDate.date.getMonthValue} AND #input.date.dayOfMonth == ${PaymentDate.date.getDayOfMonth} AND " +
          s"#input.time.hour == ${PaymentDate.date.getHour} AND #input.time.minute == ${PaymentDate.date.getMinute} AND #input.time.second == ${PaymentDate.date.getSecond} AND " +
          s"#input.decimal == ${PaymentDate.decimal} AND " +
          s"#input.uuid.mostSignificantBits == ${PaymentDate.uuid.getMostSignificantBits}L AND #input.uuid.leastSignificantBits == ${PaymentDate.uuid.getLeastSignificantBits}L"
      )
    )

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentDate.recordWithData, PaymentDate.record)
  }

  test("should define kafka key for output record") {
    val topicConfig = createAndRegisterTopicConfig("kafka-key", List(FullNameV1.schema))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, value = "#input", key = "#input.first")
    val process     = createAvroProcess(sourceParam, sinkParam, None)

    kafkaClient.createTopic(topicConfig.input.name, partitions = 1)
    pushMessage(FullNameV1.record, topicConfig.input)
    kafkaClient.createTopic(topicConfig.output.name, partitions = 1)

    run(process) {
      val consumer = kafkaClient.createConsumer()
      val consumed = consumer.consumeWithJson[String](topicConfig.output.name).take(1).head
      consumed.key() shouldEqual FullNameV1.BaseFirst
    }
  }

  test("should use null key for empty key expression") {
    val topicConfig = createAndRegisterTopicConfig("kafka-null-key", List(FullNameV1.schema))
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, value = "#input")
    val process     = createAvroProcess(sourceParam, sinkParam, None)

    kafkaClient.createTopic(topicConfig.input.name, partitions = 1)
    pushMessage(FullNameV1.record, topicConfig.input)
    kafkaClient.createTopic(topicConfig.output.name, partitions = 1)

    run(process) {
      val result = kafkaClient.createConsumer().consumeWithConsumerRecord(topicConfig.output.name).take(1).head
      result.key() shouldEqual null
    }
  }

  test("should represent avro string type as Java string") {
    val topicConfig = createAndRegisterTopicConfig("avro-string-type-test", paymentSchemas)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(2))
    val sinkParam =
      UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input", validationMode = Some(ValidationMode.lax))
    val filterParam = Some("#input.id.toLowerCase != 'we use here method that only String class has'")
    val process     = createAvroProcess(sourceParam, sinkParam, filterParam)

    runAndVerifyResultSingleEvent(process, topicConfig, PaymentV1.record, PaymentV1.record)
  }

  test("should treat key as string when source has string-as-key deserialization") {
    val topicConfig = createAndRegisterTopicConfig("kafka-generic-source-without-key-schema", Product.schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, value = "#input")
    val process     = createAvroProcess(sourceParam, sinkParam, None)

    kafkaClient.createTopic(topicConfig.input.name, partitions = 1)
    kafkaClient.createTopic(topicConfig.output.name, partitions = 1)

    import io.circe.syntax._
    val serializedKey   = SimpleJsonRecord("lorem", "ipsum").asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    val serializedValue = valueSerializer.serialize(topicConfig.input.name, Product.record)
    kafkaClient.sendRawMessage(topicConfig.input.name, serializedKey, serializedValue).futureValue

    run(process) {
      consumeAndVerifyMessages(topicConfig.output, List(Product.record))
      verifyInputMeta("""{"id":"lorem","field":"ipsum"}""", topicConfig.input, 0, 0L)
    }
  }

  test("should read key and value when source has key-value deserialization") {
    // register the same value schema for input and output topic
    val topicConfig = createAndRegisterTopicConfig("kafka-generic-source-with-key-schema", Product.schema)
    // register key schema for input topic
    registerSchema(topicConfig.input.toUnspecialized, FullNameV1.schema, isKey = true)

    // create process
    val sourceParam = SourceAvroParam.forUniversalWithKeySchemaSupport(topicConfig, LatestSchemaVersion)
    val sinkParam   = UniversalSinkParam(topicConfig, LatestSchemaVersion, value = "#input")
    val filterParam = Some(s"#inputMeta.key.first == '${FullNameV1.BaseFirst}'")
    val process     = createAvroProcess(sourceParam, sinkParam, filterParam)

    kafkaClient.createTopic(topicConfig.input.name, partitions = 1)
    pushMessageWithKey(FullNameV1.record, Product.record, topicConfig.input.name)
    kafkaClient.createTopic(topicConfig.output.name, partitions = 1)

    run(process) {
      consumeAndVerifyMessages(topicConfig.output, List(Product.record))

      // Here process uses key-and-value deserialization in a source with metadata.
      // The content of event's key is interpreted according to defined key schema.
      verifyInputMeta(FullNameV1.record, topicConfig.input, 0, 0L)
    }
  }

  private def verifyInputMeta(key: Any, topic: TopicName.ForSource, partition: Int, offset: Long): Assertion = {
    val expectedInputMeta = InputMeta(
      key = key,
      topic = topic.name,
      partition = partition,
      offset = offset,
      timestamp = 0L,
      timestampType = TimestampType.CREATE_TIME,
      headers = Map.empty[String, String].asJava,
      leaderEpoch = 0
    )

    eventually {
      val results = KafkaAvroPayloadIntegrationSpec.sinkForInputMetaResultsHolder.results.map { inputMeta =>
        (inputMeta.asScala.toMap + (InputMeta.timestampParameterName -> 0L)).asJava
      }
      results should not be empty
      results should contain(expectedInputMeta)
    }
  }

}

object KafkaAvroPayloadIntegrationSpec extends Serializable {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}

@JsonCodec case class SimpleJsonRecord(id: String, field: String)

object KafkaAvroIntegrationMockSchemaRegistry {

  val schemaRegistryMockClient: MockSchemaRegistryClient =
    new MockConfluentSchemaRegistryClientBuilder().build

}
