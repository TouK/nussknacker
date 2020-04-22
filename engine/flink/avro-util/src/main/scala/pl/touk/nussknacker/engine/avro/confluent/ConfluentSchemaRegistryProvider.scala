package pl.touk.nussknacker.engine.avro.confluent

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.confluent.formatter.ConfluentAvroToJsonFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider[T: TypeInformation](val schemaRegistryClient: ConfluentSchemaRegistryClient,
                                                          val serializationSchemaFactory: SerializationSchemaFactory[Any],
                                                          val deserializationSchemaFactory: DeserializationSchemaFactory[T],
                                                          val formatKey: Boolean) extends SchemaRegistryProvider[T] {
  override def recordFormatter(topic: String): Option[RecordFormatter] =
    Some(ConfluentAvroToJsonFormatter(schemaRegistryClient, topic, formatKey))
}

object ConfluentSchemaRegistryProvider extends {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies, useSpecificAvroReader: Boolean, formatKey: Boolean): ConfluentSchemaRegistryProvider[T] = {
    val kafkaConfig = processObjectDependencies.config.as[KafkaConfig]("kafka")
    val confluentSchemaRegistryClient = ConfluentSchemaRegistryClient(kafkaConfig)
    ConfluentSchemaRegistryProvider[T](confluentSchemaRegistryClient, None, None, useSpecificAvroReader, formatKey)
  }

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(processObjectDependencies, useSpecificAvroReader = false, formatKey = false)

  def apply[T: TypeInformation](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
                                serializationSchemaFactory: Option[SerializationSchemaFactory[Any]],
                                deserializationSchemaFactory: Option[DeserializationSchemaFactory[T]],
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =

    new ConfluentSchemaRegistryProvider[T](
      confluentSchemaRegistryClient,
      serializationSchemaFactory.getOrElse(
        defaultSerializationSchemaFactory(confluentSchemaRegistryClient)
      ),
      deserializationSchemaFactory.getOrElse(
        defaultDeserializationSchemaFactory(confluentSchemaRegistryClient, useSpecificAvroReader)
      ),
      formatKey
    )

  def apply[T: TypeInformation](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(confluentSchemaRegistryClient, None, None, useSpecificAvroReader, formatKey)

  private def defaultSerializationSchemaFactory(confluentSchemaRegistryClient: ConfluentSchemaRegistryClient) =
    new ConfluentAvroSerializationSchemaFactory(confluentSchemaRegistryClient.confluentSchemaRegistryClient)

  private def defaultDeserializationSchemaFactory[T: TypeInformation](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, useSpecificAvroReader: Boolean) =
    new ConfluentAvroDeserializationSchemaFactory[T](confluentSchemaRegistryClient.confluentSchemaRegistryClient, useSpecificAvroReader)

}
