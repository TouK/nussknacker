package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.serialization.{KafkaSchemaBasedKeyValueSerializationSchemaFactory, KafkaSchemaBasedValueSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

trait ConfluentAvroSerializerFactory {

  protected def createSerializer[T](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                    kafkaConfig: KafkaConfig,
                                    schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                    isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

    val avroSchemaOpt = schemaOpt.map(_.schema).map {
      case schema: AvroSchema => schema
      case schema => throw new IllegalArgumentException(s"Not supported schema type: ${schema.schemaType()}")
    }

    val serializer = ConfluentKafkaAvroSerializer(kafkaConfig, schemaRegistryClient, avroSchemaOpt, isKey = isKey)
    serializer.asInstanceOf[Serializer[T]]
  }
}

class ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedValueSerializationSchemaFactory with ConfluentAvroSerializerFactory {

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] =
    createSerializer[Any](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedKeyValueSerializationSchemaFactory with ConfluentAvroSerializerFactory {

  override protected def createKeySerializer(kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer[K](schemaRegistryClientFactory, kafkaConfig, None, isKey = true)

  override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer[V](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)
}
