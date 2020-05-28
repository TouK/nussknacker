package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer

import org.apache.avro.Schema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided static schema.
  *
  * @param schema
  * @param schemaRegistry
  * @param isKey
  * @tparam T
  */
class ConfluentStaticKafkaAvroDeserializer[T](schemaRegistry: ConfluentSchemaRegistryClient, schema: Schema, isKey: Boolean)
  extends ConfluentBaseKafkaAvroDeserializer(schemaRegistry, isKey) with Deserializer[T] {

  override def deserialize(topic: String, data: Array[Byte]): T =
    deserialize(data, schema).asInstanceOf[T]

  override def close(): Unit = ???
}

object ConfluentStaticKafkaAvroDeserializer {
  def apply[T](schemaRegistryClient: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean) = {
    val subject = AvroUtils.topicSubject(topic, isKey = isKey)
    val schema = schemaRegistryClient
      .getSchema(subject, version)
      .valueOr(exc => throw new SerializationException(s"Error retrieving Avro schema for topic $topic.", exc))

    new ConfluentStaticKafkaAvroDeserializer(schemaRegistryClient, schema, isKey)
  }
}
