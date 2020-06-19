package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareDeserializationSchemaFactory, KafkaVersionAwareSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider[T: TypeInformation](val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                          val serializationSchemaFactory: KafkaVersionAwareSerializationSchemaFactory[Any],
                                                          val deserializationSchemaFactory: KafkaVersionAwareDeserializationSchemaFactory[T],
                                                          val kafkaConfig: KafkaConfig,
                                                          val formatKey: Boolean) extends SchemaRegistryProvider[T] {

  def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(createSchemaRegistryClient, topic, formatKey))

  override def createSchemaRegistryClient: ConfluentSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
}

object ConfluentSchemaRegistryProvider extends Serializable {

  def apply[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                serializationSchemaFactory: Option[KafkaVersionAwareSerializationSchemaFactory[Any]],
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
        defaultDeserializationSchemaFactory(schemaRegistryClientFactory, useSpecificAvroReader)
      ),
      kafkaConfig,
      formatKey
    )

  def apply[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                processObjectDependencies: ProcessObjectDependencies,
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
    ConfluentSchemaRegistryProvider(schemaRegistryClientFactory, None, None, kafkaConfig, useSpecificAvroReader, formatKey)
  }

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(CachedConfluentSchemaRegistryClientFactory(), processObjectDependencies)

  def apply[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      processObjectDependencies,
      useSpecificAvroReader = false,
      formatKey = false
    )

  def defaultSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSerializationSchemaFactory =
    ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory)

  def defaultDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean): ConfluentKafkaAvroDeserializationSchemaFactory[T] =
    ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory, useSpecificAvroReader)
}
