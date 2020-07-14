package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.Schema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import scala.collection.JavaConverters._

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided schema.
  *
  * Important: there can be some delay between saved process schema and deploy schema, because
  *            fetching schema for deserializer has place at deploy moment. It can be happen when process has
  *            set latest version and deploy was run after new schema was added.
  */
class ConfluentKafkaAvroDeserializer[T](kafkaConfig: KafkaConfig, schema: Schema, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
                                        var isKey: Boolean, _useSpecificAvroReader: Boolean)
  extends AbstractConfluentKafkaAvroDeserializer with Deserializer[T] {

  schemaRegistry = confluentSchemaRegistryClient.client
  useSpecificAvroReader = _useSpecificAvroReader

  configure(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava, isKey)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    val deserializerConfig = new KafkaAvroDeserializerConfig(configs)
    configureClientProperties(deserializerConfig, ConfluentUtils.SchemaProvider)
    this.isKey = isKey
  }

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val record = deserialize(topic, isKey, data, schema)
    record.asInstanceOf[T]
  }

  override def close(): Unit = {}
}