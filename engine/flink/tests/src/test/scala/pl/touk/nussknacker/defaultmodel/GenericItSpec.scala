package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.{EitherValues, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaTestUtils}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit

class GenericItSpec extends FunSuite with FlinkSpec with Matchers with KafkaSpec with EitherValues with LazyLogging {

  import KafkaTestUtils._
  import MockSchemaRegistry._
  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  private val secondsToWaitForAvro = 30

  override lazy val config: Config = ConfigFactory.load()
    .withValue("components.mockKafka.config.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("components.kafka.disabled", fromAnyRef(true))
    .withValue("components.mockKafka.disabled", fromAnyRef(false))
    .withValue("components.mockKafka.config.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
    // we turn off auto registration to do it on our own passing mocked schema registry client
    .withValue(s"components.mockKafka.config.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}", fromAnyRef(false))

  lazy val mockProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "components.mockKafka.config")

  val JsonInTopic: String = "name.json.input"
  val JsonOutTopic: String = "name.json.output"

  protected val avroEncoder: BestEffortAvroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

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

  private val givenNotMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Zenon", "last" -> "Nowak"), RecordSchemaV1
  )

  private val givenMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "last" -> "Kowalski"), RecordSchemaV1
  )

  private val givenMatchingAvroObjConvertedToV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> null, "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenMatchingAvroObjV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> "Tomek", "last" -> "Kowalski"), RecordSchemaV2
  )

  private val givenSecondMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("firstname" -> "Jan"), SecondRecordSchemaV1
  )

  private def jsonTypedProcess(filter: String) =
    EspProcessBuilder
      .id("json-test")
      .parallelism(1)
      .source("start", "kafka-typed-json",
        "topic" -> s"'$JsonInTopic'",
        "type" ->
          """{
            |  "first": "String",
            |  "last": "String",
            |  "nestMap": { "nestedField": "String" },
            |  "list1": {{"listField": "String"}},
            |  "list2": { "Long" }
            |}""".stripMargin
      )
      .filter("name-filter", filter)
      .emptySink("end",  "kafka-json", "topic" -> s"'$JsonOutTopic'", "value" -> "#input")

  private def jsonSchemedProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption, validationMode: ValidationMode = ValidationMode.strict) =
    EspProcessBuilder
      .id("json-schemed-test")
      .parallelism(1)
      .source(
        "start",
        "kafka-registry-typed-json",
        KafkaAvroBaseComponentTransformer.TopicParamName -> s"'${topicConfig.input}'",
        KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink(
        "end",
        "kafka-registry-typed-json-raw",
        KafkaAvroBaseComponentTransformer.SinkKeyParamName -> "",
        KafkaAvroBaseComponentTransformer.SinkValueParamName -> "#input",
        KafkaAvroBaseComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName -> s"'${validationMode.name}'"
      )

  private def avroProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption, validationMode: ValidationMode = ValidationMode.strict) =
    EspProcessBuilder
      .id("avro-test")
      .parallelism(1)
      .source(
        "start",
        "kafka-avro",
        KafkaAvroBaseComponentTransformer.TopicParamName -> s"'${topicConfig.input}'",
        KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .filter("name-filter", "#input.first == 'Jan'")
      .emptySink(
        "end",
        "kafka-avro-raw",
        KafkaAvroBaseComponentTransformer.SinkKeyParamName -> "",
        KafkaAvroBaseComponentTransformer.SinkValueParamName -> "#input",
        KafkaAvroBaseComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName -> s"'${validationMode.name}'"

      )

  private def avroFromScratchProcess(topicConfig: TopicConfig, versionOption: SchemaVersionOption) =
    EspProcessBuilder
      .id("avro-from-scratch-test")
      .parallelism(1)
      .source(
        "start",
        "kafka-avro",
        KafkaAvroBaseComponentTransformer.TopicParamName -> s"'${topicConfig.input}'",
        KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> versionOptionParam(versionOption)
      )
      .emptySink(
        "end",
        "kafka-avro-raw",
        KafkaAvroBaseComponentTransformer.SinkKeyParamName -> "",
        KafkaAvroBaseComponentTransformer.SinkValueParamName -> s"{first: #input.first, last: #input.last}",
        KafkaAvroBaseComponentTransformer.TopicParamName -> s"'${topicConfig.output}'",
        KafkaAvroBaseComponentTransformer.SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'",
        KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> "'1'"
      )

  private def versionOptionParam(versionOption: SchemaVersionOption) =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  test("should read json object from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli

    sendAsJson(givenNotMatchingJsonObj, JsonInTopic, timeAgo)
    sendAsJson(givenMatchingJsonObj, JsonInTopic, timeAgo)

    assertThrows[Exception] {
      run(jsonTypedProcess("#input.nestMap.notExist == ''")) {}
    }

    assertThrows[Exception] {
      run(jsonTypedProcess("#input.list1[0].notExist == ''")) {}
    }

    val validJsonProcess = jsonTypedProcess("#input.first == 'Jan' and " +
      "#input.nestMap.nestedField != 'dummy' and " +
      "#input.list1[0].listField != 'dummy' and " +
      "#input.list2[0] != 15")
    run(validJsonProcess) {
      val consumer = kafkaClient.createConsumer()
      val processedMessage = consumer.consume(JsonOutTopic, secondsToWaitForAvro).head

      processedMessage.timestamp shouldBe timeAgo
      decodeJsonUnsafe[Json](processedMessage.message()) shouldEqual parseJson(givenMatchingJsonObj)
    }
  }

  test("should read avro object from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli

    val topicConfig = createAndRegisterTopicConfig("read-filter-save-avro", RecordSchemas)

    sendAvro(givenNotMatchingAvroObj, topicConfig.input)
    sendAvro(givenMatchingAvroObj, topicConfig.input, timestamp = timeAgo)

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.allowOptional)) {
      val processed = consumeOneRawAvroMessage(topicConfig.output)
      processed.timestamp shouldBe timeAgo
      valueDeserializer.deserialize(topicConfig.output, processed.message()) shouldEqual givenMatchingAvroObjConvertedToV2
    }
  }

  test("should read schemed json from kafka, filter and save it to kafka, passing timestamp") {
    val timeAgo = Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli

    val topicConfig = createAndRegisterTopicConfig("read-filter-save-json", RecordSchemas)

    sendAsJson(givenMatchingJsonObj, topicConfig.input, timeAgo)

    run(jsonSchemedProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.allowOptional)) {
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

    run(avroProcess(topicConfig, ExistingSchemaVersion(1), validationMode = ValidationMode.allowOptional)) {
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

  private lazy val creator: DefaultConfigCreator = new DefaultConfigCreator

  private var registrar: FlinkProcessRegistrar = _
  private lazy val valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)
  private lazy val valueDeserializer = new KafkaAvroDeserializer(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
  }

  private def executionConfigPreparerChain(modelData: LocalModelData) = {
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {
        override def prepareExecutionConfig(config: ExecutionConfig)(jobData: JobData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(config, new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient), kafkaConfig)
        }
      }
    )
  }

  private def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  private def sendAvro(obj: Any, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }

  private def sendAsJson(jsonString: String, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = jsonString.getBytes(StandardCharsets.UTF_8)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    */
  private def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
      schemaRegistryMockClient.register(inputSubject, parsedSchema)
      schemaRegistryMockClient.register(outputSubject, parsedSchema)
    })

    topicConfig
  }

  private def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))
}

case class TopicConfig(input: String, output: String, schemas: List[Schema], isKey: Boolean)

object TopicConfig {
  private final val inputPrefix = "test.generic.avro.input."
  private final val outputPrefix = "test.generic.avro.output."

  def apply(testName: String, schemas: List[Schema]): TopicConfig =
    new TopicConfig(inputPrefix + testName, outputPrefix + testName, schemas, isKey = false)
}

object MockSchemaRegistry extends Serializable {

  val RecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV1: Schema = AvroUtils.parseSchema(RecordSchemaStringV1)

  val RecordSchemaStringV2: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV2: Schema = AvroUtils.parseSchema(RecordSchemaStringV2)

  val RecordSchemas = List(RecordSchemaV1, RecordSchemaV2)

  val SecondRecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "firstname", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val SecondRecordSchemaV1: Schema = AvroUtils.parseSchema(SecondRecordSchemaStringV1)

  val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient

}
