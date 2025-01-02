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
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.{
  KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory,
  SchemaRegistryBasedDeserializerFactory
}

import scala.reflect.ClassTag

class FlinkKafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    deserializerFactory: SchemaRegistryBasedDeserializerFactory
) extends KafkaSchemaRegistryBasedKeyValueDeserializationSchemaFactory(
      schemaRegistryClientFactory,
      deserializerFactory
    ) {

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

      private lazy val typeInformationDetector = TypeInformationDetection.instance

      private lazy val keyTypeInfo: TypeInformation[K] = {
        if (kafkaConfig.useStringForKey) {
          Types.STRING.asInstanceOf[TypeInformation[K]]
        } else {
          // TODO: Creating TypeInformation for Avro / Json Schema is difficult because of schema evolution, therefore we rely on Kryo, e.g. serializer for GenericRecordWithSchemaId
          typeInformationDetector.forClass[K]
        }
      }

      private lazy val valueTypeInfo: TypeInformation[V] =
        // TODO: Creating TypeInformation for Avro / Json Schema is difficult because of schema evolution, therefore we rely on Kryo, e.g. serializer for GenericRecordWithSchemaId
        typeInformationDetector.forClass[V]

      override def getProducedType: TypeInformation[ConsumerRecord[K, V]] =
        new ConsumerRecordTypeInfo(keyTypeInfo, valueTypeInfo)

    }
  }

}
