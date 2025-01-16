package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.serialization.{
  KafkaSchemaBasedKeyValueSerializationSchemaFactory,
  KafkaSchemaBasedValueSerializationSchemaFactory
}

trait SchemaRegistryBasedSerializerFactory extends Serializable {

  def createSerializer(
      schemaRegistryClient: Option[SchemaRegistryClient],
      kafkaConfig: KafkaConfig,
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      isKey: Boolean
  ): Serializer[Any]

}

trait AbstractSchemaRegistryBasedSerializerFactory {

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory

  protected def serializerFactory: SchemaRegistryBasedSerializerFactory

  protected final def createSerializer(
      kafkaConfig: KafkaConfig,
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      isKey: Boolean
  ): Serializer[Any] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    serializerFactory.createSerializer(schemaRegistryClient, kafkaConfig, schemaDataOpt, isKey)
  }

}

class KafkaSchemaRegistryBasedValueSerializationSchemaFactory(
    protected val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    protected val serializerFactory: SchemaRegistryBasedSerializerFactory
) extends KafkaSchemaBasedValueSerializationSchemaFactory
    with AbstractSchemaRegistryBasedSerializerFactory {

  override protected[schemedkafka] def createValueSerializer(
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Serializer[Any] =
    createSerializer(kafkaConfig, schemaOpt, isKey = false)

}

class KafkaSchemaRegistryBasedKeyValueSerializationSchemaFactory(
    protected val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    protected val serializerFactory: SchemaRegistryBasedSerializerFactory
) extends KafkaSchemaBasedKeyValueSerializationSchemaFactory
    with AbstractSchemaRegistryBasedSerializerFactory {

  override protected[schemedkafka] def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer(kafkaConfig, None, isKey = true)

  override protected[schemedkafka] def createValueSerializer(
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Serializer[Any] =
    createSerializer(kafkaConfig, schemaOpt, isKey = false)

}
