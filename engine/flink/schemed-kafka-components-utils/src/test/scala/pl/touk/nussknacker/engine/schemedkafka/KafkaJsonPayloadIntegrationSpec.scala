package pl.touk.nussknacker.engine.schemedkafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.{BeforeAndAfter, FunSuite}
import pl.touk.nussknacker.engine.schemedkafka.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonDeserializer, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.schemedkafka.schema.{GeneratedAvroClassSampleSchema, PaymentV1}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaBasedSerdeProvider}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class KafkaJsonPayloadIntegrationSpec extends FunSuite with KafkaAvroSpecMixin with BeforeAndAfter {

  import KafkaAvroIntegrationMockSchemaRegistry._

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider =
      ConfluentSchemaBasedSerdeProvider.jsonPayload(schemaRegistryClientFactory)

    override protected def schemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
  }

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): Deserializer[Any] = SimpleKafkaJsonDeserializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config
      .withValue("kafka.avroAsJsonSerialization", fromAnyRef(true)), creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
  }

  test("should read and write json of generic record via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple-generic", PaymentV1.schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, PaymentV1.exampleData, BestEffortJsonEncoder.defaultForTests.encode(PaymentV1.exampleData))
  }

  test("should read and write json of specific record via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple-specific", GeneratedAvroClassSampleSchema.schema)
    val sourceParam = SourceAvroParam.forSpecific(topicConfig)
    val sinkParam = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    val givenObj = GeneratedAvroClassSampleSchema.specificRecord
    val expectedJson = BestEffortJsonEncoder.defaultForTests.encode(givenObj)

    runAndVerifyResult(process, topicConfig, givenObj, expectedJson, useSpecificAvroReader = true)
  }

}
