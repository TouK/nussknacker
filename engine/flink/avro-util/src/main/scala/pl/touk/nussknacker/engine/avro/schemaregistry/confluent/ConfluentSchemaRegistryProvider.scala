package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.typed.AvroSettings
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      val serializationSchemaFactoryProvider: AvroSettings => KafkaAvroSerializationSchemaFactory,
                                      val deserializationSchemaFactoryProvider: AvroSettings => KafkaAvroDeserializationSchemaFactory,
                                      kafkaConfig: KafkaConfig,
                                      formatKey: Boolean) extends SchemaRegistryProvider {

  override def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(schemaRegistryClientFactory, kafkaConfig, topic, formatKey))

  override def createSchemaRegistryClient: ConfluentSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

  override def deserializationSchemaFactory(avroSettings: AvroSettings): KafkaAvroDeserializationSchemaFactory = deserializationSchemaFactoryProvider(avroSettings)

  override def serializationSchemaFactory(avroSettings: AvroSettings): KafkaAvroSerializationSchemaFactory = serializationSchemaFactoryProvider(avroSettings)
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
      avroSettings => new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory, avroSettings),
      avroSettings => new ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory, avroSettings),
      kafkaConfig,
      formatKey)
  }
}
