package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.SchemaDeterminingStrategy.{SchemaDeterminingStrategy, _}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueSerializationSchemaFactory, KafkaVersionAwareValueSerializationSchemaFactory}

trait ConfluentAvroSerializerFactory {
  import collection.JavaConverters._

  protected def createSerializer[T](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                    schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                    topic: String,
                                    version: Option[Int],
                                    kafkaConfig: KafkaConfig,
                                    isKey: Boolean): Serializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val serializer = schemaDeterminingStrategy match {
      case SchemaDeterminingStrategy.FromSubjectVersion =>
        ConfluentKafkaAvroSerializer(schemaRegistryClient, topic, version, isKey = isKey)
      case SchemaDeterminingStrategy.FromRecord =>
        new KafkaAvroSerializer(schemaRegistryClient.client)
    }

    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    serializer.configure(props.asJava, isKey)
    serializer.asInstanceOf[Serializer[T]]
  }
}

class ConfluentAvroSerializationSchemaFactory(schemaDeterminingStrategy: SchemaDeterminingStrategy, schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareValueSerializationSchemaFactory[AnyRef] with ConfluentAvroSerializerFactory {

  override protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[AnyRef] =
    createSerializer[AnyRef](schemaDeterminingStrategy, schemaRegistryClientFactory, topic, version, kafkaConfig, isKey = false)
}

object ConfluentAvroSerializationSchemaFactory {
  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSerializationSchemaFactory =
    new ConfluentAvroSerializationSchemaFactory(FromSubjectVersion, schemaRegistryClientFactory)
}

abstract class ConfluentAvroKeyValueSerializationSchemaFactory(schemaDeterminingStrategy: SchemaDeterminingStrategy, schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareKeyValueSerializationSchemaFactory[AnyRef] with ConfluentAvroSerializerFactory {

  override protected def createKeySerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[K] =
    createSerializer[K](schemaDeterminingStrategy, schemaRegistryClientFactory, topic, version, kafkaConfig, isKey = true)

  override protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V] =
    createSerializer[V](schemaDeterminingStrategy, schemaRegistryClientFactory, topic, version, kafkaConfig, isKey = false)
}
