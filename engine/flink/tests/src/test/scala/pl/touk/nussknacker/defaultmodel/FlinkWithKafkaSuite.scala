package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistryClientHolder.MockSchemaRegistryClientProvider
import pl.touk.nussknacker.defaultmodel.SampleSchemas.RecordSchemaV1
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{JobData, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{
  ProcessSettingsPreparer,
  UnoptimizedSerializationPreparer
}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.schemedkafka.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaRegistryClientFactory,
  SchemaVersionOption
}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.test.{KafkaConfigProperties, WithConfig}

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

abstract class FlinkWithKafkaSuite
    extends AnyFunSuite
    with FlinkSpec
    with KafkaSpec
    with BeforeAndAfterAll
    with BeforeAndAfter
    with WithConfig
    with Matchers {

  private lazy val creator: DefaultConfigCreator = new TestDefaultConfigCreator

  protected var registrar: FlinkProcessRegistrar                   = _
  protected var schemaRegistryMockClient: MockSchemaRegistryClient = _
  protected var valueSerializer: KafkaAvroSerializer               = _
  protected var valueDeserializer: KafkaAvroDeserializer           = _

  protected lazy val additionalComponents: List[ComponentDefinition] = Nil

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val schemaRegistryClientProvider = MockSchemaRegistryClientHolder.registerSchemaRegistryClient()
    schemaRegistryMockClient = schemaRegistryClientProvider.schemaRegistryClient
    valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)
    valueDeserializer = new KafkaAvroDeserializer(schemaRegistryMockClient)
    val components =
      new MockFlinkKafkaComponentProvider(() => schemaRegistryClientProvider.schemaRegistryClientFactory)
        .create(kafkaComponentsConfig, ProcessObjectDependencies.withConfig(config)) :::
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components :::
        additionalComponents
    val modelData =
      LocalModelData(config, components, configCreator = creator)
    registrar = FlinkProcessRegistrar(
      new FlinkProcessCompilerDataFactory(modelData),
      FlinkJobConfig.parse(modelData.modelConfig),
      executionConfigPreparerChain(modelData, schemaRegistryClientProvider)
    )
  }

  private def executionConfigPreparerChain(
      modelData: LocalModelData,
      schemaRegistryClientProvider: MockSchemaRegistryClientProvider
  ) = {
    ExecutionConfigPreparer.chain(
      ProcessSettingsPreparer(modelData),
      new UnoptimizedSerializationPreparer(modelData),
      new ExecutionConfigPreparer {
        override def prepareExecutionConfig(
            config: ExecutionConfig
        )(jobData: JobData, deploymentData: DeploymentData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(
            config,
            schemaRegistryClientProvider.schemaRegistryClientFactory,
            kafkaConfig
          )
        }
      }
    )
  }

  protected def avroAsJsonSerialization = false

  private lazy val kafkaComponentsConfig = ConfigFactory
    .empty()
    .withValue(
      KafkaConfigProperties.bootstrapServersProperty("config"),
      fromAnyRef(kafkaServer.kafkaAddress)
    )
    .withValue(
      KafkaConfigProperties.property("config", "schema.registry.url"),
      fromAnyRef("not_used")
    )
    .withValue(
      KafkaConfigProperties.property("config", "auto.offset.reset"),
      fromAnyRef("earliest")
    )
    .withValue("config.avroAsJsonSerialization", fromAnyRef(avroAsJsonSerialization))
    // we turn off auto registration to do it on our own passing mocked schema registry client
    .withValue(
      s"config.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}",
      fromAnyRef(false)
    )

  lazy val kafkaConfig: KafkaConfig                = KafkaConfig.parseConfig(config, "config")
  protected val avroEncoder: BestEffortAvroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  protected val givenNotMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Zenon", "last" -> "Nowak"),
    RecordSchemaV1
  )

  protected val givenMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "last" -> "Kowalski"),
    RecordSchemaV1
  )

  protected def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.name.value)(action)
  }

  protected def sendAvro(obj: Any, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }

  protected def sendAsJson(jsonString: String, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = jsonString.getBytes(StandardCharsets.UTF_8)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }

  protected def versionOptionParam(versionOption: SchemaVersionOption) =
    versionOption match {
      case LatestSchemaVersion            => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  protected def createAndRegisterAvroTopicConfig(name: String, schemas: List[Schema]) =
    createAndRegisterTopicConfig(name, schemas.map(s => ConfluentUtils.convertToAvroSchema(s)))

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[ParsedSchema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject  = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      schemaRegistryMockClient.register(inputSubject, schema)
      schemaRegistryMockClient.register(outputSubject, schema)
    })

    topicConfig
  }

  protected def createAndRegisterAvroTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterAvroTopicConfig(name, List(schema))

  protected def createAndRegisterTopicConfig(name: String, schema: ParsedSchema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))

  protected def parseJson(str: String) = io.circe.parser.parse(str).toOption.get
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

case class TopicConfig(input: String, output: String, schemas: List[ParsedSchema], isKey: Boolean)

object TopicConfig {
  private final val inputPrefix  = "test.generic.avro.input."
  private final val outputPrefix = "test.generic.avro.output."

  def inputTopicName(testName: String): String = {
    inputPrefix + testName
  }

  def outputTopicName(testName: String): String = {
    outputPrefix + testName
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

  val RecordSchemas = List(RecordSchemaV1, RecordSchemaV2)

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

}

class TestDefaultConfigCreator extends DefaultConfigCreator {

  override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    Seq(LoggingListener)

}
