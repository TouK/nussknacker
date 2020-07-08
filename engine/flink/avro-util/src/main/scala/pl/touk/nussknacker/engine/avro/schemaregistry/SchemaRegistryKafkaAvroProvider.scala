package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import javax.annotation.Nullable
import org.apache.avro.Schema
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.KafkaAvroSchemaProvider
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class SchemaRegistryKafkaAvroProvider[T](schemaRegistryProvider: SchemaRegistryProvider[T],
                                         kafkaConfig: KafkaConfig,
                                         topic: String,
                                         version: Option[Int]) extends KafkaAvroSchemaProvider[T] {

  @transient private lazy val schemaRegistryClient: SchemaRegistryClient = schemaRegistryProvider.createSchemaRegistryClient

  //For typing we use all fields from schema (also optionally fields)
  override def typeDefinition: Validated[SchemaRegistryError, typing.TypingResult] =
    fetchTopicValueSchema
      .map(AvroSchemaTypeDefinitionExtractor.typeDefinition)

  override def fetchTopicValueSchema: Validated[SchemaRegistryError, Schema] =
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = false)

  override def deserializationSchema: KafkaDeserializationSchema[T] =
    schemaRegistryProvider.deserializationSchemaFactory.create(List(topic), version, kafkaConfig)

  override def serializationSchema: KafkaSerializationSchema[AnyRef] =
    schemaRegistryProvider.serializationSchemaFactory.create(topic, version, kafkaConfig)

  override def recordFormatter: Option[RecordFormatter] =
    schemaRegistryProvider.recordFormatter(topic)
}

object SchemaRegistryKafkaAvroProvider {

  // We try to cast Java Nullable Integer to Scala Int, so we can't do Option(version)
  def apply[T](schemaRegistryProvider: SchemaRegistryProvider[T], kafkaConfig: KafkaConfig, topic: String, @Nullable version: Integer): SchemaRegistryKafkaAvroProvider[T] =
    new SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, if (version == null) Option.empty else Some(version))
}
