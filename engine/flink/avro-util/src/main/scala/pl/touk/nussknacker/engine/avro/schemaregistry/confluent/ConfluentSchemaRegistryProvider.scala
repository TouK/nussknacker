package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareDeserializationSchemaFactory, KafkaVersionAwareSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

import scala.reflect.ClassTag

class ConfluentSchemaRegistryProvider[T](val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                         val serializationSchemaFactory: KafkaVersionAwareSerializationSchemaFactory[AnyRef],
                                         val deserializationSchemaFactory: KafkaVersionAwareDeserializationSchemaFactory[T],
                                         val kafkaConfig: KafkaConfig,
                                         val formatKey: Boolean) extends SchemaRegistryProvider[T] {

  def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(createSchemaRegistryClient, topic, formatKey))

  override def createSchemaRegistryClient: ConfluentSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
}

object ConfluentSchemaRegistryProvider extends Serializable {

  def apply[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                         serializationSchemaFactory: Option[KafkaVersionAwareSerializationSchemaFactory[AnyRef]],
                         deserializationSchemaFactory: Option[KafkaVersionAwareDeserializationSchemaFactory[T]],
                         kafkaConfig: KafkaConfig,
                         useSpecificAvroReader: Boolean,
                         formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =

    new ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      serializationSchemaFactory.getOrElse(
        defaultSerializationSchemaFactory(schemaRegistryClientFactory)
      ),
      deserializationSchemaFactory.getOrElse(
        defaultDeserializationSchemaFactory(schemaRegistryClientFactory)
      ),
      kafkaConfig,
      formatKey
    )

  def apply[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                         processObjectDependencies: ProcessObjectDependencies,
                         useSpecificAvroReader: Boolean,
                         formatKey: Boolean): ConfluentSchemaRegistryProvider[T] = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
    ConfluentSchemaRegistryProvider(schemaRegistryClientFactory, None, None, kafkaConfig, useSpecificAvroReader, formatKey)
  }

  def apply[T: ClassTag](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(CachedConfluentSchemaRegistryClientFactory(), processObjectDependencies)

  def apply[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      processObjectDependencies,
      useSpecificAvroReader = false,
      formatKey = false
    )

  def defaultSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSerializationSchemaFactory =
    ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory)

  def defaultDeserializationSchemaFactory[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentKafkaAvroDeserializationSchemaFactory[T] =
    ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory)
}
