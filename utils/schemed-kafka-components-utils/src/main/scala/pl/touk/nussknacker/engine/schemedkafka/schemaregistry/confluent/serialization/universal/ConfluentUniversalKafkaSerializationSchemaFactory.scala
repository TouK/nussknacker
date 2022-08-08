package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.UniversalComponentsSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedValueSerializationSchemaFactory

class ConfluentUniversalKafkaSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] = {
    val schema: ParsedSchema = schemaOpt.map(_.schema).getOrElse(throw new IllegalArgumentException("SchemaData should be defined for universal serializer"))
    val client = schemaRegistryClientFactory.create(kafkaConfig)
    UniversalComponentsSupport.forSchemaType(schema.schemaType())
      .serializer[Any](schema, client, kafkaConfig, isKey = false)
  }
}
