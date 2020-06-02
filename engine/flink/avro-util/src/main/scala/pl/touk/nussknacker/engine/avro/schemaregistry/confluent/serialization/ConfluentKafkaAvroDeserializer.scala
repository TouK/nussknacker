package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util

import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer, KafkaAvroDeserializerConfig}
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided fresh schema by topic / version.
  *
  * @param confluentSchemaRegistryClient
  * @param topic
  * @param version
  * @param isKey
  * @tparam T
  */
class ConfluentKafkaAvroDeserializer[T](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], var isKey: Boolean)
  extends AbstractKafkaAvroDeserializer with Deserializer[T] with ConfluentKafkaAvroSerialization {

  schemaRegistry = confluentSchemaRegistryClient.client

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val schema = schemaByTopicAndVersion(confluentSchemaRegistryClient, this.topic, version, isKey)
    val record = deserialize(data, schema)
    record.asInstanceOf[T]
  }

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val deserializerConfig = new KafkaAvroDeserializerConfig(configs)
    configureClientProperties(deserializerConfig)
    useSpecificAvroReader = deserializerConfig.getBoolean("specific.avro.reader")
    this.isKey = isKey
  }

  override def close(): Unit = {

  }
}
