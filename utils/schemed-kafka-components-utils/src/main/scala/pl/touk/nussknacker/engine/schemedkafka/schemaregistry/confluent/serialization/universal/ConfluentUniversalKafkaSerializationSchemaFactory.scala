package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.UniversalSchemaSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class ConfluentUniversalKafkaSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] = {
    val schema: ParsedSchema = schemaOpt.map(_.schema).getOrElse(throw new IllegalArgumentException("SchemaData should be defined for universal serializer"))
    val client = schemaRegistryClientFactory.create(kafkaConfig)
    UniversalSchemaSupport.forSchemaType(schema.schemaType())
      .serializer[Any](schema, client, kafkaConfig, isKey = false)
  }
}
