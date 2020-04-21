package pl.touk.nussknacker.engine.avro.confluent

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.formatter.AvroToJsonFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

class ConfluentSchemaRegistryProvider[T: TypeInformation](var schemaRegistryClient: ConfluentSchemaRegistryClient,
                                      var serializationSchemaFactory: SerializationSchemaFactory[Any],
                                      var deserializationSchemaFactory: DeserializationSchemaFactory[T],
                                      var timestampAssigner: Option[TimestampAssigner[T]]) extends SchemaRegistryProvider[T] with Serializable {
  override def recordFormatter(topic: String, formatKey: Boolean): RecordFormatter =
    AvroToJsonFormatter(schemaRegistryClient.confluentClient, topic, formatKey)
}
object ConfluentSchemaRegistryProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies, useSpecificAvroReader: Boolean, formatKey: Boolean): ConfluentSchemaRegistryProvider[T] = {
    val kafkaConfig = processObjectDependencies.config.as[KafkaConfig]("kafka")
    val confluentSchemaRegistryClient = ConfluentSchemaRegistryClient(kafkaConfig)

    new ConfluentSchemaRegistryProvider[T](
      confluentSchemaRegistryClient,
      defaultAvroSerializer(confluentSchemaRegistryClient),
      defaultAvroDeserializer(confluentSchemaRegistryClient, useSpecificAvroReader),
      Option.empty
    )
  }

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies, useSpecificAvroReader: Boolean): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(processObjectDependencies, useSpecificAvroReader, formatKey = false)

  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(processObjectDependencies, useSpecificAvroReader = false, formatKey = false)

  def apply[T: TypeInformation](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient,
                                serializationSchemaFactory: Option[SerializationSchemaFactory[Any]],
                                deserializationSchemaFactory: Option[DeserializationSchemaFactory[T]],
                                useSpecificAvroReader: Boolean,
                                formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =
    new ConfluentSchemaRegistryProvider[T](
      confluentSchemaRegistryClient,
      serializationSchemaFactory.getOrElse(defaultAvroSerializer(confluentSchemaRegistryClient)),
      deserializationSchemaFactory.getOrElse(defaultAvroDeserializer(confluentSchemaRegistryClient, useSpecificAvroReader)),
      Option.empty
    )

  def apply[T: TypeInformation](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, useSpecificAvroReader: Boolean, formatKey: Boolean): ConfluentSchemaRegistryProvider[T] =
    ConfluentSchemaRegistryProvider(confluentSchemaRegistryClient, None, None, useSpecificAvroReader, formatKey)

  private def defaultAvroSerializer(confluentSchemaRegistryClient: ConfluentSchemaRegistryClient) =
    new ConfluentAvroSerializationSchemaFactory(confluentSchemaRegistryClient.confluentClient)

  private def defaultAvroDeserializer[T: TypeInformation](confluentSchemaRegistryClient: ConfluentSchemaRegistryClient, useSpecificAvroReader: Boolean) =
    new ConfluentAvroDeserializationSchemaFactory[T](confluentSchemaRegistryClient.confluentClient, useSpecificAvroReader)

}
