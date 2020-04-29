package pl.touk.nussknacker.engine.avro.schemaregistry

import javax.annotation.Nullable
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class SchemaRegistryKafkaAvroProvider[T](schemaRegistryProvider: SchemaRegistryProvider[T],
                                         kafkaConfig: KafkaConfig,
                                         topic: String,
                                         version: Option[Int]) extends KafkaAvroSchemaProvider[T] {

  override def typeDefinition: typing.TypingResult =
    AvroSchemaTypeDefinitionExtractor.typeDefinition(
      schemaRegistryProvider.createSchemaRegistryClient.getSchema(
        AvroUtils.valueSubject(topic), version
      )
    )

  override def deserializationSchema: KafkaDeserializationSchema[T] =
    schemaRegistryProvider.deserializationSchemaFactory.create(List(topic), kafkaConfig)

  override def serializationSchema: KafkaSerializationSchema[Any] =
    schemaRegistryProvider.serializationSchemaFactory.create(topic, kafkaConfig)

  override def recordFormatter: Option[RecordFormatter] =
    schemaRegistryProvider.recordFormatter(topic)
}

object SchemaRegistryKafkaAvroProvider {
  def apply[T](schemaRegistryProvider: SchemaRegistryProvider[T], kafkaConfig: KafkaConfig, topic: String): SchemaRegistryKafkaAvroProvider[T] =
    new SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, Option.empty)

  def apply[T](schemaRegistryProvider: SchemaRegistryProvider[T], kafkaConfig: KafkaConfig, topic: String, version: Int): SchemaRegistryKafkaAvroProvider[T] =
    new SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, Some(version))

  def apply[T](schemaRegistryProvider: SchemaRegistryProvider[T], kafkaConfig: KafkaConfig, topic: String, @Nullable version: Integer): SchemaRegistryKafkaAvroProvider[T] =
    new SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, Option(version))
}
