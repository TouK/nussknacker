package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect.ClassTag
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

trait ConfluentKafkaSchemaBasedDeserializerFactory extends LazyLogging {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val avroSchemaDataOpt = schemaDataOpt.map { schemaData =>
      schemaData.schema match {
        case _: AvroSchema => schemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
        // TODO: handle json schema
        case other => throw new IllegalArgumentException(s"Unsupported schema class: ${other.getClass}")
      }
    }
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, avroSchemaDataOpt, schemaRegistryClient, _isKey = isKey, AvroUtils.isSpecificRecord[T])
  }

  protected def extractTopic(topics: List[String]): String = {
    if (topics.length > 1) {
      throw new SerializationException(s"Topics list has more then one element: $topics.")
    }
    topics.head
  }
}

class ConfluentKeyValueKafkaSchemaBasedDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory with ConfluentKafkaSchemaBasedDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

}
