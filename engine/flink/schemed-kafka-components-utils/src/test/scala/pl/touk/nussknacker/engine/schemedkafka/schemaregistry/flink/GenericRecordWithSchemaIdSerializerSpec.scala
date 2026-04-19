package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.flink.util.InstantiationUtil
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  EmptySchemaRegistry,
  GenericRecordWithSchemaId,
  SchemaRegistryClientFactoryWithRegistration,
  SchemaRegistryClientWithRegistration
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.DefaultConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink.GenericRecordWithSchemaIdSerializerSpec.DelegatingMockSchemaRegistryFactory

import java.io.ByteArrayInputStream

class GenericRecordWithSchemaIdSerializerSpec extends AnyFunSuite with Matchers {

  import AvroSerializerTestData._

  private def createConfiguredSerializer(): GenericRecordWithSchemaIdSerializer = {
    val confluentUrl = "http://confluent"
    val azureUrl     = "http://azure"

    def createSchemaRegistryConfig(url: String) =
      SchemaRegistryClientKafkaConfig(Map("schema.registry.url" -> url))

    new GenericRecordWithSchemaIdSerializer(
      new DelegatingMockSchemaRegistryFactory(
        Map(
          confluentUrl -> new DefaultConfluentSchemaRegistryClient(createConfluentClient()),
          azureUrl     -> createAzureClient(),
        )
      ),
      Map(
        confluentSchemaRegistryId -> createSchemaRegistryConfig(confluentUrl),
        azureSchemaRegistryId     -> createSchemaRegistryConfig(azureUrl),
      )
    )
  }

  test("serialize/deserialize - single records") {
    testRoundTrips(List(confluentRecord1, confluentRecord2, azureRecord))
  }

  test("serialize/deserialize - record stream") {
    val serializer = createConfiguredSerializer()
    val kryo       = new Kryo

    val output = new Output(1024)
    serializer.write(kryo, output, confluentRecord1)
    serializer.write(kryo, output, confluentRecord2)
    serializer.write(kryo, output, azureRecord)

    val input       = new Input(new ByteArrayInputStream(output.getBuffer))
    val readRecord1 = serializer.read(kryo, input, classOf[GenericRecordWithSchemaId])
    val readRecord2 = serializer.read(kryo, input, classOf[GenericRecordWithSchemaId])
    val readRecord3 = serializer.read(kryo, input, classOf[GenericRecordWithSchemaId])

    input.position() shouldBe output.position()

    readRecord1 shouldBe confluentRecord1
    readRecord2 shouldBe confluentRecord2
    readRecord3 shouldBe azureRecord
  }

  test("can be serialized and deserialized") {
    testRoundTrips(
      List(confluentRecord1, confluentRecord2, azureRecord),
      { s =>
        val serializedSerializer = InstantiationUtil.serializeObject(s)
        InstantiationUtil.deserializeObject[GenericRecordWithSchemaIdSerializer](
          serializedSerializer,
          Thread.currentThread().getContextClassLoader
        )
      }
    )
  }

  test("freshly configured instance can deserialize old data") {
    testRoundTrips(List(confluentRecord1, confluentRecord2, azureRecord), { _ => createConfiguredSerializer() })
  }

  def testRoundTrips(
      records: Iterable[GenericRecordWithSchemaId],
      copy: GenericRecordWithSchemaIdSerializer => GenericRecordWithSchemaIdSerializer = { s => s }
  ): Unit = {
    records.zipWithIndex.foreach { case (record, index) =>
      withClue(s"record #$index") {
        val serializer = createConfiguredSerializer()
        serializeAndDeserialize(serializer, record, copy)
      }
    }
  }

  private def serializeAndDeserialize(
      serializer: GenericRecordWithSchemaIdSerializer,
      record: GenericRecordWithSchemaId,
      copySerializer: GenericRecordWithSchemaIdSerializer => GenericRecordWithSchemaIdSerializer
  ): Unit = {
    val output = new Output(1024)
    serializer.write(new Kryo, output, record)

    val deserializer = copySerializer(serializer)
    serializer shouldBe deserializer
    System.identityHashCode(serializer) !== System.identityHashCode(deserializer)

    val readRecord = serializer.read(new Kryo, new Input(output.getBuffer), classOf[GenericRecordWithSchemaId])
    readRecord shouldBe record
  }

}

object GenericRecordWithSchemaIdSerializerSpec {

  class DelegatingMockSchemaRegistryFactory(clients: => Map[String, SchemaRegistryClientWithRegistration])
      extends SchemaRegistryClientFactoryWithRegistration {

    override type SchemaRegistryClientT = SchemaRegistryClientWithRegistration

    override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT =
      config.kafkaProperties
        .get("schema.registry.url")
        .flatMap(clients.get)
        .getOrElse(EmptySchemaRegistry)

    // simplified equals, required to ensure that serializer instances can be compared
    override def equals(obj: Any): Boolean = obj match {
      case _: DelegatingMockSchemaRegistryFactory => true
      case _                                      => false
    }

  }

}
