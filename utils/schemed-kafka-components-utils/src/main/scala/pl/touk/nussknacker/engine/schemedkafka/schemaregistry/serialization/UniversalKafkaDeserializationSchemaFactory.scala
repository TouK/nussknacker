package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalKafkaDeserializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ChainedSchemaIdFromMessageExtractor, SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

class UniversalKafkaDeserializationSchemaFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    createSchemaIdFromMessageExtractor: SchemaRegistryClient => ChainedSchemaIdFromMessageExtractor
) extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory {

  override protected def createKeyDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K] =
    createDeserializer[K](kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[V] =
    createDeserializer[V](kafkaConfig, schemaDataOpt, isKey = false)

  private final def createDeserializer[T](
      kafkaConfig: KafkaConfig,
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      isKey: Boolean
  ): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    new UniversalKafkaDeserializer[T](
      schemaRegistryClient,
      kafkaConfig,
      createSchemaIdFromMessageExtractor(schemaRegistryClient),
      schemaDataOpt,
      isKey
    )
  }

}
