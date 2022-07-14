package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistry.{RecordSchemaV1, schemaRegistryMockClient}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer.{ProcessSettingsPreparer, UnoptimizedSerializationPreparer}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.WithConfig

abstract class FlinkWithKafkaSuite extends FunSuite with FlinkSpec with KafkaSpec with BeforeAndAfterAll with BeforeAndAfter with WithConfig with Matchers {

  private lazy val creator: DefaultConfigCreator = new DefaultConfigCreator

  protected var registrar: FlinkProcessRegistrar = _
  protected lazy val valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)
  protected lazy val valueDeserializer = new KafkaAvroDeserializer(schemaRegistryMockClient)

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
        override def prepareExecutionConfig(config: ExecutionConfig)(jobData: JobData, deploymentData: DeploymentData): Unit = {
          AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(config, new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient), kafkaConfig)
        }
      }
    )
  }

  override lazy val config: Config = ConfigFactory.load()
    .withValue("components.mockKafka.config.kafkaAddress", fromAnyRef(kafkaServer.kafkaAddress))
    .withValue("components.kafka.disabled", fromAnyRef(true))
    .withValue("components.mockKafka.disabled", fromAnyRef(false))
    .withValue("components.mockKafka.config.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
    // we turn off auto registration to do it on our own passing mocked schema registry client
    .withValue(s"components.mockKafka.config.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}", fromAnyRef(false))

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "components.mockKafka.config")
  protected val avroEncoder: BestEffortAvroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  protected val givenNotMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Zenon", "last" -> "Nowak"), RecordSchemaV1
  )

  protected val givenMatchingAvroObj = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "last" -> "Kowalski"), RecordSchemaV1
  )


  protected def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  protected def sendAvro(obj: Any, topic: String, timestamp: java.lang.Long = null) = {
    val serializedObj = valueSerializer.serialize(topic, obj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, timestamp = timestamp)
  }


  protected def versionOptionParam(versionOption: SchemaVersionOption) =
    versionOption match {
      case LatestSchemaVersion => s"'${SchemaVersionOption.LatestOptionName}'"
      case ExistingSchemaVersion(version) => s"'$version'"
    }

  /**
    * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
    * and we can have wrong results of tests..
    */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
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

  protected def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
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
