package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.UniversalSchemaSupport._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class ConfluentUniversalKafkaSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] = {
    val schema: ParsedSchema =  schemaOpt.map(_.schema).getOrElse(throw new IllegalArgumentException("SchemaData should be defined for universal serializer"))
    val createSerializer = schema.serializerFactory[Any]
    val client = schemaRegistryClientFactory.create(kafkaConfig)

    createSerializer(client, kafkaConfig, false)
  }
}
