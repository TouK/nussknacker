package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryClientFactory.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider[T: TypeInformation](val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                          val serializationSchemaFactory: SerializationSchemaFactory[Any],
                                                          val deserializationSchemaFactory: DeserializationSchemaFactory[T],
                                                          val kafkaConfig: KafkaConfig,
                                                          val formatKey: Boolean) extends SchemaRegistryProvider[T] {
  def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(createSchemaRegistryClient, topic, formatKey))

  override def createSchemaRegistryClient: ConfluentSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
}

object ConfluentSchemaRegistryProvider extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                serializationSchemaFactory: Option[SerializationSchemaFactory[Any]],
                                deserializationSchemaFactory: Option[DeserializationSchemaFactory[T]],
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
    val kafkaConfig = processObjectDependencies.config.as[KafkaConfig]("kafka")
    ConfluentSchemaRegistryProvider(schemaRegistryClientFactory, None, None, kafkaConfig, useSpecificAvroReader, formatKey)
  }

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(
      CachedConfluentSchemaRegistryClientFactory,
      processObjectDependencies,
      useSpecificAvroReader = false,
      formatKey = false
    )

  def defaultSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) =
    new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory)

  def defaultDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean) =
    new ConfluentAvroDeserializationSchemaFactory(schemaRegistryClientFactory, useSpecificAvroReader)

}
