package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareDeserializationSchemaFactory, KafkaVersionAwareSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

import scala.reflect.ClassTag

class ConfluentSchemaRegistryProvider[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                         val serializationSchemaFactory: KafkaVersionAwareSerializationSchemaFactory[AnyRef],
                                         val deserializationSchemaFactory: KafkaVersionAwareDeserializationSchemaFactory[T],
                                         kafkaConfig: KafkaConfig,
                                         formatKey: Boolean) extends SchemaRegistryProvider[T] {

  override def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(createSchemaRegistryClient, topic, formatKey))

  override def createSchemaRegistryClient: ConfluentSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

}

object ConfluentSchemaRegistryProvider extends Serializable {

  def apply[T: ClassTag](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(CachedConfluentSchemaRegistryClientFactory(), processObjectDependencies)

  def apply[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      processObjectDependencies,
      formatKey = false
    )

  def apply[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                         processObjectDependencies: ProcessObjectDependencies,
                         formatKey: Boolean): ConfluentSchemaRegistryProvider[T] = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
    new ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      ConfluentAvroSerializationSchemaFactory(kafkaConfig, schemaRegistryClientFactory),
      ConfluentKafkaAvroDeserializationSchemaFactory(kafkaConfig, schemaRegistryClientFactory),
      kafkaConfig,
      formatKey)
  }
}
