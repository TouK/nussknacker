package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedSerializerFactory

object UniversalSerializerFactory extends SchemaRegistryBasedSerializerFactory {

  override def createSerializer(
      schemaRegistryClient: Option[SchemaRegistryClient],
      kafkaConfig: KafkaConfig,
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      isKey: Boolean
  ): Serializer[Any] = {
    val schema: ParsedSchema = schemaDataOpt
      .map(_.schema)
      .getOrElse(throw new IllegalArgumentException("SchemaData should be defined for universal serializer"))
    UniversalSchemaSupportDispatcher(kafkaConfig)
      .forSchemaType(schema.schemaType())
      .serializer(Some(schema), schemaRegistryClient, isKey = false)
  }

}
