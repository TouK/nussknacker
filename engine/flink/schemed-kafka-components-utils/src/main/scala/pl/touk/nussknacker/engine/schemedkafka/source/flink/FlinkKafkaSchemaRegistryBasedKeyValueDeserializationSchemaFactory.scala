package pl.touk.nussknacker.engine.schemedkafka.source.flink

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordKafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.flink.typeinfo.ConsumerRecordTypeInfo
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{AbstractSchemaRegistryBasedDeserializerFactory, SchemaRegistryBasedDeserializerFactory}
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

import scala.reflect.ClassTag

class FlinkKafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
    protected val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    protected val deserializerFactory: SchemaRegistryBasedDeserializerFactory
) extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory
    with AbstractSchemaRegistryBasedDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K] =
    createDeserializer[K](kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[V] =
    createDeserializer[V](kafkaConfig, schemaDataOpt, isKey = false)

  override def create[K: ClassTag, V: ClassTag](
      kafkaConfig: KafkaConfig,
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]]
  ): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

    // We extend by ResultTypeQueryable because we want to use Flink TypeInformation
    new ConsumerRecordKafkaDeserializationSchema[K, V] with ResultTypeQueryable[ConsumerRecord[K, V]] {

      @transient
      override protected lazy val keyDeserializer: Deserializer[K] =
        createKeyOrUseStringDeserializer[K](keySchemaDataOpt, kafkaConfig)

      @transient
      override protected lazy val valueDeserializer: Deserializer[V] =
        createValueDeserializer[V](valueSchemaDataOpt, kafkaConfig)

      @transient
      private lazy val typeInformationDetector = TypeInformationDetection.instance

      @transient
      private val keyTypeInfo: TypeInformation[K] = {
        if (kafkaConfig.useStringForKey) {
          Types.STRING.asInstanceOf[TypeInformation[K]]
        } else {
          typeInformationDetector.forClass[K] //We use Kryo serializer here
        }
      }

      @transient
      private val valueTypeInfo: TypeInformation[V] =
        typeInformationDetector.forClass[V] //We use Kryo serializer here

      override def getProducedType: TypeInformation[ConsumerRecord[K, V]] =
        new ConsumerRecordTypeInfo(keyTypeInfo, valueTypeInfo)

    }
  }

}
