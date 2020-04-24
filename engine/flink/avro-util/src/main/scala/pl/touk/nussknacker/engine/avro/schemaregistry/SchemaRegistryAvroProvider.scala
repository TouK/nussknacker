package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, SchemaAvroProvider}
import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}

class SchemaRegistryAvroProvider[T](schemaRegistryProvider: SchemaRegistryProvider[T]) extends SchemaAvroProvider[T] {

  override def typeDefinition(topic: String, version: Option[Int], avro: Option[String]): typing.TypingResult =
    AvroSchemaTypeDefinitionExtractor.typeDefinition(
      schemaRegistryProvider.createSchemaRegistryClient.getSchema(
        AvroUtils.valueSubject(topic), version
      )
    )

  override def deserializationSchemaFactory(avro: Option[String]): DeserializationSchemaFactory[T] =
    schemaRegistryProvider.deserializationSchemaFactory

  override def serializationSchemaFactory(avro: Option[String]): SerializationSchemaFactory[Any] =
    schemaRegistryProvider.serializationSchemaFactory

  override def recordFormatter(topic: String, avro: Option[String]): Option[RecordFormatter] =
    schemaRegistryProvider.recordFormatter(topic)

}
