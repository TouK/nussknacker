package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

import scala.reflect.ClassTag

trait SchemaRegistryBasedDeserializerFactory extends Serializable {

  def createDeserializer[T: ClassTag](schemaRegistryClient: SchemaRegistryClient,
                                      kafkaConfig: KafkaConfig,
                                      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                      isKey: Boolean): Deserializer[T]

}

trait AbstractSchemaRegistryBasedDeserializerFactory {

  protected val schemaRegistryClientFactory: SchemaRegistryClientFactory

  protected val deserializerFactory: SchemaRegistryBasedDeserializerFactory

  protected final def createDeserializer[T: ClassTag](kafkaConfig: KafkaConfig,
                                                      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                      isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    deserializerFactory.createDeserializer[T](schemaRegistryClient, kafkaConfig, schemaDataOpt, isKey)
  }

}

class KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(protected val schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                                   protected val deserializerFactory: SchemaRegistryBasedDeserializerFactory)
  extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory with AbstractSchemaRegistryBasedDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](kafkaConfig, schemaDataOpt, isKey = false)

}
