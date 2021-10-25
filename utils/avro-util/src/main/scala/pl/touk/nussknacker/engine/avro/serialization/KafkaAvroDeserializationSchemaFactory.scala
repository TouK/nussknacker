package pl.touk.nussknacker.engine.avro.serialization

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

import scala.reflect.ClassTag

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional avro related information.
  */
trait KafkaAvroDeserializationSchemaFactory extends Serializable {

  /**
    * Prepare Flink's KafkaDeserializationSchema based on provided information.
    *
    * @param kafkaConfig        Configuration of integration with Kafka.
    * @param keySchemaDataOpt   Schema which will be used as a key reader schema.
    * @param valueSchemaDataOpt Schema which will be used as a value reader schema. In case of None, writer schema will be used.
    * @tparam K Type that should be produced by key deserialization schema.
    * @tparam V Type that should be produced by value deserialization schema. It is important parameter, because factory can
    *           use other deserialization strategy base on it or provide different TypeInformation
    * @return KafkaDeserializationSchema
    */
  def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                       keySchemaDataOpt: Option[RuntimeSchemaData],
                                       valueSchemaDataOpt: Option[RuntimeSchemaData]
                                      ): KafkaDeserializationSchema[ConsumerRecord[K, V]]

}
