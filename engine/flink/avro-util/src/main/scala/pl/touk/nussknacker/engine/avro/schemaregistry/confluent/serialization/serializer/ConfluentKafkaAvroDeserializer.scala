package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer

import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityChecker
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
class ConfluentKafkaAvroDeserializer[T](schemaCompatibilityChecker: AvroCompatibilityChecker, schemaRegistry: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean)
  extends ConfluentBaseKafkaAvroDeserializer[T](schemaCompatibilityChecker, schemaRegistry, isKey) {

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val schema = schemaByTopicAndVersion(this.topic, version)
    deserializeToSchema(data, schema)
  }

  override def close(): Unit = {}
}

object ConfluentKafkaAvroDeserializer {
  def apply[T](schemaRegistry: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean): ConfluentKafkaAvroDeserializer[T] =
    new ConfluentKafkaAvroDeserializer(AvroCompatibilityChecker.FULL_TRANSITIVE_CHECKER, schemaRegistry, topic, version, isKey)
}
