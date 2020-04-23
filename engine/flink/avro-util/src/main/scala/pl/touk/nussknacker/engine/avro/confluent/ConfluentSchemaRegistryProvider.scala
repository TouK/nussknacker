package pl.touk.nussknacker.engine.avro.confluent

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => ConfluentSchemaRegistryClient}
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.confluent.ConfluentSchemaRegistryClientFactory.TypedSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.{SchemaRegistryClientFactory, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider[T: TypeInformation](val schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient],
                                                          val serializationSchemaFactory: SerializationSchemaFactory[Any],
                                                          val deserializationSchemaFactory: DeserializationSchemaFactory[T],
                                                          val kafkaConfig: KafkaConfig,
                                                          val formatKey: Boolean) extends SchemaRegistryProvider[T] {
  override def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(createSchemaRegistryClient, topic, formatKey))

  override def createSchemaRegistryClient: TypedSchemaRegistryClient =
    schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
}

object ConfluentSchemaRegistryProvider extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient],
                                processObjectDependencies: ProcessObjectDependencies,
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] = {
    val kafkaConfig = processObjectDependencies.config.as[KafkaConfig]("kafka")
    ConfluentSchemaRegistryProvider[T](schemaRegistryClientFactory, None, None, kafkaConfig, useSpecificAvroReader, formatKey)
  }

  def apply[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient],
                                processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(schemaRegistryClientFactory, processObjectDependencies, useSpecificAvroReader = false, formatKey = false)

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies,
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider[T](ConfluentSchemaRegistryClientFactory, processObjectDependencies, useSpecificAvroReader, formatKey)

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(processObjectDependencies, useSpecificAvroReader = false, formatKey = false)

  def apply[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient],
                                serializationSchemaFactory: Option[SerializationSchemaFactory[Any]],
                                deserializationSchemaFactory: Option[DeserializationSchemaFactory[T]],
                                kafkaConfig: KafkaConfig,
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =

    new ConfluentSchemaRegistryProvider[T](
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

  def apply[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient],
                                kafkaConfig: KafkaConfig,
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(schemaRegistryClientFactory, None, None, kafkaConfig, useSpecificAvroReader, formatKey)

  def defaultSerializationSchemaFactory(schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient]) =
    new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory)

  def defaultDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory[ConfluentSchemaRegistryClient],
                                                                      useSpecificAvroReader: Boolean) =
    new ConfluentAvroDeserializationSchemaFactory[T](schemaRegistryClientFactory, useSpecificAvroReader)

}
