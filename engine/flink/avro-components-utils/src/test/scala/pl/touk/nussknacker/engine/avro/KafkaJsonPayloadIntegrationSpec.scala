package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.{BeforeAndAfter, FunSuite}
import pl.touk.nussknacker.engine.avro.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonDeserializer, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.avro.schema.{GeneratedAvroClassSampleSchema, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentAvroSchemaBasedMessagesSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaBasedMessagesSerdeProvider}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class KafkaJsonPayloadIntegrationSpec extends FunSuite with KafkaAvroSpecMixin with BeforeAndAfter {

  import KafkaAvroIntegrationMockSchemaRegistry._

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaBasedMessagesSerdeProvider: SchemaBasedMessagesSerdeProvider[AvroSchema] =
      ConfluentAvroSchemaBasedMessagesSerdeProvider.avroSchemaJsonPayload(schemaRegistryClientFactory)

    override protected def schemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
  }

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected lazy val schemaBasedMessagesSerdeProvider: ConfluentAvroSchemaBasedMessagesSerdeProvider = ConfluentAvroSchemaBasedMessagesSerdeProvider.avroSchemaJsonPayload(confluentClientFactory)

  override protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): Deserializer[Any] = SimpleKafkaJsonDeserializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
  }

  test("should read and write json of generic record via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple-generic", PaymentV1.schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, PaymentV1.exampleData, BestEffortJsonEncoder.defaultForTests.encode(PaymentV1.exampleData))
  }

  test("should read and write json of specific record via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple-specific", GeneratedAvroClassSampleSchema.schema)
    val sourceParam = SourceAvroParam.forSpecific(topicConfig)
    val sinkParam = SinkAvroParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    val givenObj = GeneratedAvroClassSampleSchema.specificRecord
    val expectedJson = BestEffortJsonEncoder.defaultForTests.encode(givenObj)

    runAndVerifyResult(process, topicConfig, givenObj, expectedJson, useSpecificAvroReader = true)
  }

}
