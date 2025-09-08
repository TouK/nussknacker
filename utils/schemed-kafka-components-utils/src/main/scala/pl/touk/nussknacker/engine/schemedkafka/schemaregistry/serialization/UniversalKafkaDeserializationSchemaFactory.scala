package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ChainedSchemaIdFromMessageExtractor,
  SchemaRegistryClient,
  SchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalKafkaDeserializer
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

class UniversalKafkaDeserializationSchemaFactory(
    override protected val kafkaComponentsConfig: KafkaComponentsConfig,
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    createSchemaIdFromMessageExtractor: SchemaRegistryClient => ChainedSchemaIdFromMessageExtractor
) extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory {

  override protected def createKeyDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
  ): Deserializer[K] =
    createDeserializer[K](schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
  ): Deserializer[V] =
    createDeserializer[V](schemaDataOpt, isKey = false)

  private final def createDeserializer[T](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      isKey: Boolean
  ): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaComponentsConfig)
    new UniversalKafkaDeserializer[T](
      schemaRegistryClient,
      kafkaComponentsConfig,
      createSchemaIdFromMessageExtractor(schemaRegistryClient),
      schemaDataOpt,
      isKey
    )
  }

}
