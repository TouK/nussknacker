package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.kryo

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.apache.flink.core.memory.{DataInputDeserializer, DataOutputSerializer}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.avro.schemaregistry.GenericRecordWithSchemaId
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.kryo.SchemaIdBasedAvroGenericRecordSerializerSpec.schema
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class SchemaIdBasedAvroGenericRecordSerializerSpec extends FunSuite with Matchers {

  test("should be able to duplicate serializer after use") {

    val config = KafkaConfig("dummy:9092", None, None, None)
    val factory = new MockConfluentSchemaRegistryClientFactory(SchemaIdBasedAvroGenericRecordSerializerSpec.schemaRegistryClient)

    val ec = new ExecutionConfig
    new SchemaIdBasedAvroGenericRecordSerializer(factory, config).registerIn(ec)

    val kryoS = new KryoSerializer(classOf[GenericRecordWithSchemaId], ec)
    checkSerializationRoundTrip(kryoS)
    //we check if SchemaIdBasedAvroGenericRecordSerializer can *really* be duplicated and that it still works...
    checkSerializationRoundTrip(kryoS.duplicate())
  }


  private def checkSerializationRoundTrip(serializer: KryoSerializer[GenericRecordWithSchemaId]) = {
    val output = new DataOutputSerializer(100)
    val record = new GenericRecordWithSchemaId(schema, SchemaIdBasedAvroGenericRecordSerializerSpec.id)
    serializer.serialize(record, output)
    val afterRoundTrip = serializer.deserialize(new DataInputDeserializer(output.getCopyOfBuffer))
    afterRoundTrip shouldBe record
  }
}

object SchemaIdBasedAvroGenericRecordSerializerSpec {

  val schema: Schema = SchemaBuilder.record("name")
    .fields().nullableString("f1", "").endRecord()

  //we put it in object to avoid serialization problems
  val (schemaRegistryClient, id) = {
    val client = new MockSchemaRegistryClient
    val id = client.register("t1", new AvroSchema(schema))
    (client, id)
  }

}
