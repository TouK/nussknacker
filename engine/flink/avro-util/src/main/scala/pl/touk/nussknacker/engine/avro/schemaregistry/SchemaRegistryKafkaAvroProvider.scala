package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import javax.annotation.Nullable
import org.apache.avro.Schema
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.KafkaAvroSchemaProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class SchemaRegistryKafkaAvroProvider[T](schemaRegistryProvider: SchemaRegistryProvider[T],
                                         kafkaConfig: KafkaConfig,
                                         topic: String,
                                         version: Option[Int]) extends KafkaAvroSchemaProvider[T] {

  //For typing we use all fields from schema (also optionally fields)
  override def typeDefinition: Validated[SchemaRegistryError, typing.TypingResult] =
    fetchValueSchema.map(AvroSchemaTypeDefinitionExtractor.typeDefinition)

  override def fetchValueSchema: Validated[SchemaRegistryError, Schema] =
    schemaRegistryProvider
      .createSchemaRegistryClient
      //There should be passed topic, we should use ConfluentUtils in confluent implementation..
      .getFreshSchema(ConfluentUtils.valueSubject(topic), version)

  override def deserializationSchema: KafkaDeserializationSchema[T] =
    schemaRegistryProvider.deserializationSchemaFactory.create(List(topic), version, kafkaConfig)

  override def serializationSchema: KafkaSerializationSchema[Any] =
    schemaRegistryProvider.serializationSchemaFactory.create(topic, version, kafkaConfig)

  override def recordFormatter: Option[RecordFormatter] =
    schemaRegistryProvider.recordFormatter(topic)
}

object SchemaRegistryKafkaAvroProvider {

  // We try to cast Java Nullable Integer to Scala Int, so we can't do Option(version)
  def apply[T](schemaRegistryProvider: SchemaRegistryProvider[T], kafkaConfig: KafkaConfig, topic: String, @Nullable version: Integer): SchemaRegistryKafkaAvroProvider[T] =
    new SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, if (version == null) Option.empty else Some(version))
}
