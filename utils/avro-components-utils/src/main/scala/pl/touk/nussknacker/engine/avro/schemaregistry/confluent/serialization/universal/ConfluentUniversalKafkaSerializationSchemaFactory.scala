package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.UniversalSchemaSupport
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedValueSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig


class ConfluentUniversalKafkaSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] =
    UniversalSchemaSupport.createSerializer(schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)
}
