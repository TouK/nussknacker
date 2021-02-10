package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaRegistryProvider, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      val serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                                      val deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                                      kafkaConfig: KafkaConfig,
                                      formatKey: Boolean) extends SchemaRegistryProvider {

  override def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(schemaRegistryClientFactory, kafkaConfig, topic, formatKey))

  override def createSchemaRegistryClient: ConfluentSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

  override def validateSchema(schema: Schema): ValidatedNel[SchemaRegistryError, Schema] =
    /* kafka-avro-serializer does not support Array at top level
    [https://github.com/confluentinc/schema-registry/issues/1298] */
    if (schema.getType == Schema.Type.ARRAY)
      Invalid(NonEmptyList.of(
        SchemaRegistryUnsupportedTypeError("Unsupported Avro type. Top level Arrays are not supported")))
    else
      Valid(schema)
}

object ConfluentSchemaRegistryProvider extends Serializable {

  def apply(processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(CachedConfluentSchemaRegistryClientFactory(), processObjectDependencies)

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      processObjectDependencies,
      formatKey = false
    )

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
            processObjectDependencies: ProcessObjectDependencies,
            formatKey: Boolean): ConfluentSchemaRegistryProvider = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config)
    new ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory),
      kafkaConfig,
      formatKey)
  }
}
