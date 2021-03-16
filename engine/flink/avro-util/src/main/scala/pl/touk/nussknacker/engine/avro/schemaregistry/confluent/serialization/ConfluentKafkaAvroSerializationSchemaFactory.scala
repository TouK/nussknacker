package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.avro.Schema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroKeyValueSerializationSchemaFactory, KafkaAvroValueSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentAvroSerializerFactory {

  protected def createSerializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                    kafkaConfig: KafkaConfig,
                                    schemaOpt: Option[Schema],
                                    version: Option[Int],
                                    isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val avroSchemaOpt = schemaOpt.map(ConfluentUtils.convertToAvroSchema(_, version))

    val serializer = ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, avroSchemaOpt, isKey = isKey)
    serializer.asInstanceOf[Serializer[T]]
  }
}

class ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueSerializationSchemaFactory with ConfluentAvroSerializerFactory {

  override protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer[Any](schemaRegistryClientFactory, kafkaConfig, schemaOpt, version, isKey = false)
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueSerializationSchemaFactory with ConfluentAvroSerializerFactory {

  override protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer[K](schemaRegistryClientFactory, kafkaConfig, None, None, isKey = true)

  override protected def createValueSerializer(schemaOpt: Option[Schema], version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer[V](schemaRegistryClientFactory, kafkaConfig, schemaOpt, version, isKey = false)
}
