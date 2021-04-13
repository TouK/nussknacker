package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.util

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.kryo.KryoGenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.collection.JavaConverters._

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided schema.
  */
class ConfluentKafkaAvroDeserializer[T](kafkaConfig: KafkaConfig, schemaData: RuntimeSchemaData, confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
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

  override protected def schemaIdSerializationEnabled: Boolean =
    KryoGenericRecordSchemaIdSerializationSupport.schemaIdSerializationEnabled(kafkaConfig)

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val deserializedData = deserialize(topic, isKey, data, schemaData)
    deserializedData.asInstanceOf[T]
  }

  override def close(): Unit = {}

}
