package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.apache.flink.core.memory.{DataInputDeserializer, DataOutputSerializer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, SchemaId}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

class GenericRecordWithSchemaIdSerializerSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private lazy val schema1: Schema = SchemaBuilder
    .record("schema1")
    .fields()
    .requiredString("f1")
    .endRecord()

  private lazy val schema2: Schema = SchemaBuilder
    .record("schema2")
    .fields()
    .requiredInt("f2")
    .endRecord()

  // we put it in object to avoid serialization problems
  private lazy val (schemaRegistryClient1, schemaId1) = {
    val client = new MockSchemaRegistryClient
    val id     = client.register("t1", new AvroSchema(schema1))
    (client, SchemaId.fromInt(id))
  }

  private lazy val (schemaRegistryClient2, schemaId2) = {
    val client = new MockSchemaRegistryClient
    val id     = client.register("t1", new AvroSchema(schema2))
    (client, SchemaId.fromInt(id))
  }

  private val schemaRegistry1Id = 6
  private val schemaRegistry2Id = 0

  override protected def afterAll(): Unit = {
    GenericRecordWithSchemaIdSerializer.clearRegistrations()
  }

  test("should be able to serialize/deserialize") {
    val config = KafkaComponentsConfig(Map("bootstrap.servers" -> "dummy:9092"), None, None)

    val serializerConfig = new SerializerConfigImpl()
    serializerConfig.registerTypeWithKryoSerializer(
      classOf[GenericRecordWithSchemaId],
      classOf[GenericRecordWithSchemaIdSerializer]
    )

    GenericRecordWithSchemaIdSerializer.register(
      schemaRegistry1Id,
      MockSchemaRegistryClientFactory
        .confluentBased(schemaRegistryClient1)
        .create(config.schemaRegistryClientKafkaConfig)
    )
    GenericRecordWithSchemaIdSerializer.register(
      schemaRegistry2Id,
      MockSchemaRegistryClientFactory
        .confluentBased(schemaRegistryClient2)
        .create(config.schemaRegistryClientKafkaConfig)
    )

    val record1 = new GenericRecordWithSchemaId(schema1, schemaRegistry1Id, schemaId1)
    record1.put("f1", "str1")
    val record2 = new GenericRecordWithSchemaId(schema2, schemaRegistry2Id, schemaId2)
    record2.put("f2", 5)

    val serializer = new KryoSerializer(classOf[GenericRecordWithSchemaId], serializerConfig)
    checkSerializationRoundTrip(serializer, record1)
    checkSerializationRoundTrip(serializer, record2)

    // check if SchemaIdBasedAvroGenericRecordSerializer can *really* be duplicated and that it still works
    checkSerializationRoundTrip(serializer.duplicate(), record1)
    checkSerializationRoundTrip(serializer.duplicate(), record2)
  }

  private def checkSerializationRoundTrip(
      serializer: KryoSerializer[GenericRecordWithSchemaId],
      record: GenericRecordWithSchemaId
  ) = {
    val output = new DataOutputSerializer(100)
    serializer.serialize(record, output)
    val afterRoundTrip = serializer.deserialize(new DataInputDeserializer(output.getCopyOfBuffer))
    afterRoundTrip shouldBe record
  }

}
