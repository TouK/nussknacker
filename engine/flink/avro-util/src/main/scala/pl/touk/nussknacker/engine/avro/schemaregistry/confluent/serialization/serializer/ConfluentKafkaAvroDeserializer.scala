package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided fresh schema by topic / version.
  *
  * @param topic
  * @param version
  * @param schemaRegistry
  * @param isKey
  * @tparam T
  */
class ConfluentKafkaAvroDeserializer[T](schemaRegistry: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean)
  extends ConfluentBaseKafkaAvroDeserializer(schemaRegistry, isKey) with Deserializer[T] {

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val subject = AvroUtils.topicSubject(this.topic, isKey)
    val schema = schemaRegistry
      .getFreshSchema(subject, version)
      .valueOr(exc => throw new SerializationException(s"Error retrieving Avro schema for topic $topic.", exc))

    deserialize(data, schema).asInstanceOf[T]
  }

  override def close(): Unit = ???
}
