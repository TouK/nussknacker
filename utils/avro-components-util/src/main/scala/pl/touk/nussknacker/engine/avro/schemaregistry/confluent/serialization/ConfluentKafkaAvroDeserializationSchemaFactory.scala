package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory extends LazyLogging {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt, schemaRegistryClient, isKey = isKey, AvroUtils.isSpecificRecord[T])
  }

  protected def extractTopic(topics: List[String]): String = {
    if (topics.length > 1) {
      throw new SerializationException(s"Topics list has more then one element: $topics.")
    }
    topics.head
  }
}

class ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

}
