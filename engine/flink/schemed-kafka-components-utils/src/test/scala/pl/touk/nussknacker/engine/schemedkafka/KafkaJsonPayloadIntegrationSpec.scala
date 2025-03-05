package pl.touk.nussknacker.engine.schemedkafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaJsonPayloadIntegrationSpec.sinkForInputMetaResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.helpers.{
  KafkaAvroSpecMixin,
  SimpleKafkaJsonDeserializer,
  SimpleKafkaJsonSerializer
}
import pl.touk.nussknacker.engine.schemedkafka.schema.PaymentV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

class KafkaJsonPayloadIntegrationSpec extends AnyFunSuite with KafkaAvroSpecMixin with BeforeAndAfter {

  import KafkaAvroIntegrationMockSchemaRegistry._

  private lazy val creator: KafkaAvroTestProcessConfigCreator =
    new KafkaAvroTestProcessConfigCreator(sinkForInputMetaResultsHolder) {
      override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
        MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
    }

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

  override protected def prepareValueDeserializer: Deserializer[Any] =
    SimpleKafkaJsonDeserializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    modelData = LocalModelData(
      config
        .withValue("kafka.avroAsJsonSerialization", fromAnyRef(true)),
      List.empty,
      configCreator = creator,
    )
  }

  test("should read and write json of generic record via avro schema") {
    val topicConfig = createAndRegisterTopicConfig("simple-generic", PaymentV1.schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResultSingleEvent(
      process,
      topicConfig,
      PaymentV1.exampleData,
      ToJsonEncoder.defaultForTests.encode(PaymentV1.exampleData)
    )
  }

}

object KafkaJsonPayloadIntegrationSpec {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}
