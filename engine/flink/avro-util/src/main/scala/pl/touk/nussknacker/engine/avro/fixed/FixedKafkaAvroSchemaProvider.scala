package pl.touk.nussknacker.engine.avro.fixed

import cats.data.Validated
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, FixedConfluentSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareDeserializationSchemaFactory, KafkaVersionAwareSerializationSchemaFactory}
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
                                                       val avroSchemaString: String,
                                                       val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                       val deserializationSchemaFactory: KafkaVersionAwareDeserializationSchemaFactory[T],
                                                       val serializationSchemaFactory: KafkaVersionAwareSerializationSchemaFactory[Any],
                                                       val kafkaConfig: KafkaConfig,
                                                       val formatKey: Boolean) extends KafkaAvroSchemaProvider[T] {

  override def deserializationSchema: KafkaDeserializationSchema[T] =
    deserializationSchemaFactory.create(List(topic), None, kafkaConfig)

  override def serializationSchema: KafkaSerializationSchema[Any] =
    serializationSchemaFactory.create(topic, None, kafkaConfig)

  override def typeDefinition: Validated[SchemaRegistryError, typing.TypingResult] =
    schemaRegistryClientFactory
      .createSchemaRegistryClient(kafkaConfig)
      .getFreshSchema(AvroUtils.valueSubject(topic), None)
      .map(AvroSchemaTypeDefinitionExtractor.typeDefinition)

  override def recordFormatter: Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(
      schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig),
      AvroUtils.valueSubject(topic),
      formatKey
    ))
}

object FixedKafkaAvroSchemaProvider {

  def fixedClientFactory(topic: String, avroSchemaString: String): ConfluentSchemaRegistryClientFactory =
    new ConfluentSchemaRegistryClientFactory {
      override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
        new FixedConfluentSchemaRegistryClient(AvroUtils.valueSubject(topic), avroSchemaString)
    }

  def apply[T: TypeInformation](topic: String, avroSchemaString: String, kafkaConfig: KafkaConfig, formatKey: Boolean, useSpecificAvroReader: Boolean): FixedKafkaAvroSchemaProvider[T] = {
    val schemaRegistryClientFactory = fixedClientFactory(topic, avroSchemaString)
    val deserializationSchemaFactory = ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory, useSpecificAvroReader)
    val serializationSchemaFactory = new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory)

    new FixedKafkaAvroSchemaProvider(
      topic,
      avroSchemaString,
      schemaRegistryClientFactory,
      deserializationSchemaFactory,
      serializationSchemaFactory,
      kafkaConfig,
      formatKey
    )
  }
}
