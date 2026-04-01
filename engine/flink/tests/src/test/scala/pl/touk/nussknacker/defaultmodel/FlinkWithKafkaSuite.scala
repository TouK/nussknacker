package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistryClientHolder.MockSchemaRegistryClientProvider
import pl.touk.nussknacker.defaultmodel.SampleSchemas.RecordSchemaV1
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentDependencies}
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.encode.ToAvroSchemaBasedEncoder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaRegistryClientFactory,
  SchemaVersionOption
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{KafkaConfigProperties, WithModelConfig}

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future

abstract class FlinkWithKafkaSuite
    extends AnyFunSuite
    with FlinkSpec
    with KafkaSpec
    with BeforeAndAfterAll
    with BeforeAndAfter
    with WithModelConfig
    with Matchers {

  override protected val kafkaComponentsConfigPrefix: String = "components.kafka.config"

  protected var schemaRegistryMockClient: MockSchemaRegistryClient = _
  protected var valueSerializer: KafkaAvroSerializer               = _
  protected var valueDeserializer: KafkaAvroDeserializer           = _
  protected var extraComponents: List[ComponentDefinition]         = _
  protected var testScenarioRunner: FlinkTestScenarioRunner        = _

  protected lazy val additionalComponents: List[ComponentDefinition] = Nil

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val schemaRegistryClientProvider = MockSchemaRegistryClientHolder.registerSchemaRegistryClient()
    schemaRegistryMockClient = schemaRegistryClientProvider.schemaRegistryClient
    valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)
    valueDeserializer = new KafkaAvroDeserializer(schemaRegistryMockClient)
    extraComponents = createFinkKafkaComponentProvider(schemaRegistryClientProvider)
      .create(
        modelConfig.getConfig("components.kafka"),
        ComponentDependencies(ModelConfig.parse(modelConfig), designerDbRef = None)
      ) :::
      additionalComponents
    testScenarioRunner = TestScenarioRunner
      .flinkBased(modelConfig, flinkMiniCluster)
      .withExtraComponents(extraComponents)
      .withExtraSerializersRegistrars(List((_: Config, _: ExecutionConfig) => {
        AvroSerializersRegistrar.registerGenericRecordSchemaIdSerialization(
          schemaRegistryClientProvider.schemaRegistryClientFactory,
          kafkaComponentsConfig.copy(
            optimizedGenericRecordSerialization = kafkaComponentsConfig.optimizedGenericRecordSerialization.copy(
              schemaRegistryId = Some(1)
            )
          )
        )
      }))
      .build()
  }

  override protected def afterAll(): Unit = {
    AvroSerializersRegistrar.clearRegistrations()
    super.afterAll()
  }

  protected def createFinkKafkaComponentProvider(
      schemaRegistryClientProvider: MockSchemaRegistryClientProvider
  ): FlinkKafkaComponentProvider = {
    new MockFlinkKafkaComponentProvider(() => schemaRegistryClientProvider.schemaRegistryClientFactory)
  }

  protected def avroAsJsonSerialization = false

  override protected def resolveModelConfig(config: Config): Config = {
    val baseModelConfig = super
      .resolveModelConfig(config)
      .withValue(s"$kafkaComponentsConfigPrefix.avroAsJsonSerialization", fromAnyRef(avroAsJsonSerialization))
      .withValue(s"$kafkaComponentsConfigPrefix.topicsExistenceValidationConfig.enabled", fromAnyRef(false))
      // we turn off auto registration to do it on our own passing mocked schema registry client
      .withValue(
        s"$kafkaComponentsConfigPrefix.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}",
        fromAnyRef(false)
      )
    maybeAddSchemaRegistryUrl(baseModelConfig)
  }

  protected def maybeAddSchemaRegistryUrl(config: Config): Config = config.withValue(
    KafkaConfigProperties.property(kafkaComponentsConfigPrefix, "schema.registry.url"),
    fromAnyRef("not_used")
  )

  protected val avroEncoder: ToAvroSchemaBasedEncoder = ToAvroSchemaBasedEncoder(ValidationMode.strict)

  protected val givenNotMatchingAvroObj: GenericData.Record = avroEncoder.encodeRecordOrError(
    Map("first" -> "Zenon", "last" -> "Nowak"),
    RecordSchemaV1
  )

  protected val givenMatchingAvroObj: GenericData.Record = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "last" -> "Kowalski"),
    RecordSchemaV1
  )

  protected def sendAvro(
      obj: Any,
      topic: TopicName.ForSource,
      timestamp: java.lang.Long = null
  ): Future[RecordMetadata] = {
    val serializedObj = valueSerializer.serialize(topic.name, obj)
    kafkaClient.sendRawMessage(topic.name, Array.empty, serializedObj, timestamp = timestamp)
  }

  protected def sendAsJson(
      jsonString: String,
      topic: TopicName.ForSource,
      timestamp: java.lang.Long = null
  ): Future[RecordMetadata] = {
    val serializedObj = jsonString.getBytes(StandardCharsets.UTF_8)
    kafkaClient.sendRawMessage(topic.name, Array.empty, serializedObj, timestamp = timestamp)
  }

  protected def versionOptionParam(versionOption: SchemaVersionOption): String =
    versionOption match {
      case LatestSchemaVersion            => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  protected def createAndRegisterAvroTopicConfig(name: String, schemas: List[Schema]): TopicConfig =
    createAndRegisterTopicConfig(name, schemas.map(s => ConfluentUtils.convertToAvroSchema(s)))

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[ParsedSchema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject  = ConfluentUtils.topicSubject(topicConfig.input.toUnspecialized, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output.toUnspecialized, topicConfig.isKey)
      schemaRegistryMockClient.register(inputSubject, schema)
      schemaRegistryMockClient.register(outputSubject, schema)
    })

    topicConfig
  }

  protected def createAndRegisterAvroTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterAvroTopicConfig(name, List(schema))

  protected def createAndRegisterTopicConfig(name: String, schema: ParsedSchema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))

  protected def parseJson(str: String): Json = io.circe.parser.parse(str).toOption.get
}

object MockSchemaRegistryClientHolder extends Serializable {

  val clientsByRunId = new ConcurrentHashMap[TestRunId, MockSchemaRegistryClient]

  def registerSchemaRegistryClient(): MockSchemaRegistryClientProvider = {
    val runId                    = TestRunId.generate
    val schemaRegistryMockClient = new MockSchemaRegistryClient
    MockSchemaRegistryClientHolder.clientsByRunId.put(runId, schemaRegistryMockClient)
    new MockSchemaRegistryClientProvider(runId)
  }

  class MockSchemaRegistryClientProvider(runId: TestRunId) extends Serializable {

    @transient
    lazy val schemaRegistryClient: MockSchemaRegistryClient = clientsByRunId.get(runId)

    @transient
    lazy val schemaRegistryClientFactory: SchemaRegistryClientFactory =
      MockSchemaRegistryClientFactory.confluentBased(clientsByRunId.get(runId))

  }

}

case class TopicConfig(
    input: TopicName.ForSource,
    output: TopicName.ForSink,
    schemas: List[ParsedSchema],
    isKey: Boolean
)

object TopicConfig {
  private final val inputPrefix  = "test.generic.avro.input."
  private final val outputPrefix = "test.generic.avro.output."

  def inputTopicName(testName: String): TopicName.ForSource = {
    TopicName.ForSource(inputPrefix + testName)
  }

  def outputTopicName(testName: String): TopicName.ForSink = {
    TopicName.ForSink(outputPrefix + testName)
  }

  def apply(testName: String, schemas: List[ParsedSchema]): TopicConfig =
    new TopicConfig(inputTopicName(testName), outputTopicName(testName), schemas, isKey = false)
}

object SampleSchemas {

  val RecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
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
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val RecordSchemaV2: Schema = AvroUtils.parseSchema(RecordSchemaStringV2)

  val RecordSchemas: List[Schema] = List(RecordSchemaV1, RecordSchemaV2)

  val SecondRecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "firstname", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val SecondRecordSchemaV1: Schema = AvroUtils.parseSchema(SecondRecordSchemaStringV1)

  val ThirdRecordSchemaStringV1: String =
    """{
      |  "type": "record",
      |  "namespace": "",
      |  "name": "UserProfile",
      |  "fields": [
      |    { "name": "first", "type": "string", "default": "Jan" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string", "default": "Kowalski" },
      |    { "name": "age", "type": "int", "default": 18 },
      |    { "name": "height", "type": "float", "default": 1.80 },
      |    { "name": "weight", "type": "double", "default": 70.5 },
      |    { "name": "lastLogin", "type": { "type": "int", "logicalType": "time-millis" }}
      |  ]
      |}
    """.stripMargin

  val ThirdRecordSchema: Schema = AvroUtils.parseSchema(ThirdRecordSchemaStringV1)

}
