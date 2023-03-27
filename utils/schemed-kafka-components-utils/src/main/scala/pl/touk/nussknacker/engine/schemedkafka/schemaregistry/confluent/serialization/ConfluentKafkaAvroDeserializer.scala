package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{GenericRecordSchemaIdSerializationSupport, SchemaRegistryBasedDeserializerFactory}

import java.util
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
  * This is Kafka Avro Deserialization class. All events will be deserialized to provided schema.
  */
class ConfluentKafkaAvroDeserializer[T](kafkaConfig: KafkaConfig, schemaData: Option[RuntimeSchemaData[AvroSchema]],
                                        confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
                                        _isKey: Boolean, _useSpecificAvroReader: Boolean)
  extends AbstractConfluentKafkaAvroDeserializer with Deserializer[T] {

  schemaRegistry = confluentSchemaRegistryClient.client
  useSpecificAvroReader = _useSpecificAvroReader

  configure(kafkaConfig.kafkaProperties.getOrElse(Map.empty).asJava, _isKey)

  override def configure(configs: util.Map[String, _], _isKey: Boolean): Unit = {
    val deserializerConfig = new KafkaAvroDeserializerConfig(configs)
    configureClientProperties(deserializerConfig, ConfluentUtils.SchemaProvider)
    isKey = _isKey
  }

  override protected val genericRecordSchemaIdSerializationSupport: GenericRecordSchemaIdSerializationSupport =
    GenericRecordSchemaIdSerializationSupport(kafkaConfig)

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val deserializedData = deserialize(topic, isKey, data, schemaData)
    deserializedData.asInstanceOf[T]
  }

  override def close(): Unit = {}

}

object ConfluentAvroDeserializerFactory extends SchemaRegistryBasedDeserializerFactory {

  def createDeserializer[T: ClassTag](schemaRegistryClient: SchemaRegistryClient,
                                      kafkaConfig: KafkaConfig,
                                      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                      isKey: Boolean): Deserializer[T] = {
    val avroSchemaDataOpt = schemaDataOpt.map { schemaData =>
      schemaData.schema match {
        case _: AvroSchema => schemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
        case other => throw new IllegalArgumentException(s"Unsupported schema class: ${other.getClass}")
      }
    }
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, avroSchemaDataOpt, schemaRegistryClient.asInstanceOf[ConfluentSchemaRegistryClient], _isKey = isKey, AvroUtils.isSpecificRecord[T])
  }

}