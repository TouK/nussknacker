package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util

import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.avro.Schema
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient

/**
  * This is Kafka Avro Serializer class. All events will be serialized to provided schema.
  *
  * @Important: there can be some delay between saved process schema and deploy schema, because
  *            fetching schema for serializer has place at deploy moment. It can be happen when process has
  *            set latest version and deploy was run after new schema was added.
  *
  * @param confluentSchemaRegistryClient
  * @param schema
  * @param schemaId
  * @param isKey
  * @tparam T
  */
class ConfluentKafkaAvroSerializer[T](schema: Schema, schemaId: Int, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, var isKey: Boolean)
  extends AbstractConfluentKafkaAvroSerializer with Serializer[T] {

  schemaRegistry = confluentSchemaRegistryClient.client

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    configureClientProperties(new KafkaAvroSerializerConfig(configs))
    this.isKey = isKey
  }

  override def serialize(topic: String, data: T): Array[Byte] =
    serialize(schema, schemaId, data)

  override def close(): Unit = {}
}

object ConfluentKafkaAvroSerializer extends ConfluentKafkaAvroSerializationMixin {
  def apply[T](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, topic: String, version: Option[Int], isKey: Boolean): ConfluentKafkaAvroSerializer[T] = {
    val schema = fetchSchema(confluentSchemaRegistryClient, topic, version, isKey = isKey)
    val schemaId = fetchSchemaId(confluentSchemaRegistryClient, topic, schema, isKey = isKey)
    new ConfluentKafkaAvroSerializer(schema, schemaId, confluentSchemaRegistryClient, isKey = isKey)
  }
}
