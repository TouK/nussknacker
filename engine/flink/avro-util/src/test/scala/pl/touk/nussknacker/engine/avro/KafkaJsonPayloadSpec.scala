package pl.touk.nussknacker.engine.avro

import java.nio.charset.StandardCharsets

import org.apache.avro.Schema
import org.scalatest.BeforeAndAfter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.schema.PaymentV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadDeserializerFactory, ConfluentJsonPayloadSerializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class KafkaJsonPayloadSpec  extends KafkaAvroSpecMixin with BeforeAndAfter {

  import KafkaAvroIntegrationMockSchemaRegistry._

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaRegistryProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider = {
      val clientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
      val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config)
      new ConfluentSchemaRegistryProvider(clientFactory,
        new ConfluentJsonPayloadSerializerFactory(clientFactory),
        new ConfluentJsonPayloadDeserializerFactory(clientFactory),
        kafkaConfig, false)
    }
  }

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false)

  protected val paymentSchemas: List[Schema] = List(PaymentV1.schema)

  override def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), config, executionConfigPreparerChain(modelData))
  }

  after {
    recordingExceptionHandler.clear()
  }

  test("should read and write json via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple", PaymentV1.schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, PaymentV1.exampleData, encoder.encode(PaymentV1.exampleData))
  }

  override protected def serialize(objectTopic: String, obj: Any): Array[Byte] = {
    encoder.encode(obj).spaces2.getBytes(StandardCharsets.UTF_8)
  }

  override protected def deserialize(useSpecificAvroReader: Boolean)(objectTopic: String, obj: Array[Byte]): Any = {
    io.circe.parser.parse(new String(obj, StandardCharsets.UTF_8)).right.get
  }
}
