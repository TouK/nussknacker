package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import org.apache.kafka.common.serialization.Deserializer
import org.scalatest.{BeforeAndAfter, FunSuite}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonDeserializer, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.avro.schema.PaymentV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData

class KafkaJsonPayloadSpec extends FunSuite with KafkaAvroSpecMixin with BeforeAndAfter {

  import KafkaAvroIntegrationMockSchemaRegistry._

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaRegistryProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider = {
      val clientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
      ConfluentSchemaRegistryProvider.jsonPayload(clientFactory, processObjectDependencies, formatKey = false)
    }
  }
  
  protected val paymentSchemas: List[Schema] = List(PaymentV1.schema)

  override def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
  }

  after {
    recordingExceptionHandler.clear()
  }

  test("should read and write json via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple", PaymentV1.schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, PaymentV1.exampleData, valueSerializer.encoder.encode(PaymentV1.exampleData))
  }

  override protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): Deserializer[Any] = SimpleKafkaJsonDeserializer

  override protected def valueSerializer: SimpleKafkaJsonSerializer.type = SimpleKafkaJsonSerializer
}
