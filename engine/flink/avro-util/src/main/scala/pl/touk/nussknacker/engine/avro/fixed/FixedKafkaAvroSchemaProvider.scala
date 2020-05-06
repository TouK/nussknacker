package pl.touk.nussknacker.engine.avro.fixed

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentAvroDeserializationSchemaFactory, ConfluentAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

/**
  * This Provider based on fixed avro raw data without schema id. It will cause changing strategy of
  * test data generation - it should use embedded schema.
  *
  * We use Confluent MockSchemaRegistryClient to mock fixed schema and Confluent deserializer and serializer
  *
  * @TODO: In future we should create own serializator and deserializator for fixed schema
  */
class FixedKafkaAvroSchemaProvider[T: TypeInformation](val topic: String,
                                                       val avroSchema: String,
                                                       val kafkaConfig: KafkaConfig,
                                                       val formatKey: Boolean,
                                                       val useSpecificAvroReader: Boolean) extends KafkaAvroSchemaProvider[T] {

  lazy val factory: ConfluentSchemaRegistryClientFactory = new ConfluentSchemaRegistryClientFactory {
    override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
      new FixedConfluentSchemaRegistryClient(avroSchema)
  }

  lazy val deserializationSchemaFactory = new ConfluentAvroDeserializationSchemaFactory(factory, useSpecificAvroReader)

  lazy val serializationSchemaFactory = new ConfluentAvroSerializationSchemaFactory(factory)

  override def deserializationSchema: KafkaDeserializationSchema[T] =
    deserializationSchemaFactory.create(List(topic), kafkaConfig)

  override def serializationSchema: KafkaSerializationSchema[Any] =
    serializationSchemaFactory.create(topic, kafkaConfig)

  override def typeDefinition: Validated[SchemaRegistryError, typing.TypingResult] =
    Valid(AvroSchemaTypeDefinitionExtractor.typeDefinition(AvroUtils.parseSchema(avroSchema)))

  override def recordFormatter: Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(factory.createSchemaRegistryClient(kafkaConfig), topic, formatKey))
}
