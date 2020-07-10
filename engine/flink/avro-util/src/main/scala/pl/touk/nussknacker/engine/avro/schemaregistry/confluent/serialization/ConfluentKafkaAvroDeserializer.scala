package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided schema.
  *
  * @Important: there can be some delay between saved process schema and deploy schema, because
  *            fetching schema for deserializer has place at deploy moment. It can be happen when process has
  *            set latest version and deploy was run after new schema was added.
  *
  * @param confluentSchemaRegistryClient
  * @param schema
  * @param isKey
  * @tparam T
  */
class ConfluentKafkaAvroDeserializer[T](schema: Schema, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, var isKey: Boolean)
  extends AbstractConfluentKafkaAvroDeserializer with Deserializer[T] {

  schemaRegistry = confluentSchemaRegistryClient.client

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val record = deserialize(topic, isKey, data, schema)
    record.asInstanceOf[T]
  }

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val deserializerConfig = new KafkaAvroDeserializerConfig(configs)
    configureClientProperties(deserializerConfig, ConfluentUtils.SchemaProvider)
    useSpecificAvroReader = deserializerConfig.getBoolean("specific.avro.reader")
    this.isKey = isKey
  }

  override def close(): Unit = {}
}