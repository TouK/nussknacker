package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serializer

import org.apache.avro.Schema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided static schema.
  *
  * @param schema
  * @param schemaRegistry
  * @param isKey
  * @tparam T
  */
class ConfluentStaticKafkaAvroDeserializer[T](schema: Schema, schemaRegistry: ConfluentSchemaRegistryClient, isKey: Boolean)
  extends ConfluentBaseKafkaAvroDeserializer(schemaRegistry, isKey) with Deserializer[T] {

  override def deserialize(topic: String, data: Array[Byte]): T =
    deserialize(data, schema).asInstanceOf[T]

  override def close(): Unit = ???
}
