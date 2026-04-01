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

  private lazy val schema: Schema = SchemaBuilder
    .record("name")
    .fields()
    .nullableString("f1", "")
    .endRecord()

  // we put it in object to avoid serialization problems
  private lazy val (schemaRegistryClient, schemaId) = {
    val client = new MockSchemaRegistryClient
    val id     = client.register("t1", new AvroSchema(schema))
    (client, SchemaId.fromInt(id))
  }

  private val schemaRegistryId = 6

  override protected def afterAll(): Unit = {
    GenericRecordWithSchemaIdSerializer.clearRegistrations()
  }

  test("should be able to serialize and duplicate serializer after use") {
    val config  = KafkaComponentsConfig(Map("bootstrap.servers" -> "dummy:9092"), None, None)
    val factory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryClient)

    val serializerConfig = new SerializerConfigImpl()
    serializerConfig.registerTypeWithKryoSerializer(
      classOf[GenericRecordWithSchemaId],
      classOf[GenericRecordWithSchemaIdSerializer]
    )

    GenericRecordWithSchemaIdSerializer.register(1, factory.create(config.schemaRegistryClientKafkaConfig))

    val serializer = new KryoSerializer(classOf[GenericRecordWithSchemaId], serializerConfig)
    checkSerializationRoundTrip(serializer)

    // check if SchemaIdBasedAvroGenericRecordSerializer can *really* be duplicated and that it still works
    checkSerializationRoundTrip(serializer.duplicate())
  }

  private def checkSerializationRoundTrip(serializer: KryoSerializer[GenericRecordWithSchemaId]) = {
    val output = new DataOutputSerializer(100)
    val record = new GenericRecordWithSchemaId(schema, schemaRegistryId, schemaId)
    serializer.serialize(record, output)
    val afterRoundTrip = serializer.deserialize(new DataInputDeserializer(output.getCopyOfBuffer))
    afterRoundTrip shouldBe record
  }

}
