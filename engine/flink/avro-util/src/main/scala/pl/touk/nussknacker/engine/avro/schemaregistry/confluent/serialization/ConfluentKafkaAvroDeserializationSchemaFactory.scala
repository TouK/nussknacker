package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.specific.SpecificRecordBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesAvroTypeInfo, LogicalTypesGenericRecordAvroTypeInfo, LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.kryo.KryoGenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroKeyValueDeserializationSchemaFactory, KafkaAvroValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory extends LazyLogging {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt.orNull, schemaRegistryClient, isKey = isKey, AvroUtils.isSpecificRecord[T])
  }

  protected def createTypeInfo[T: ClassTag](kafkaConfig: KafkaConfig, schemaDataOpt: Option[RuntimeSchemaData]): TypeInformation[T] = {
    ConfluentUtils.typeInfoForSchema(kafkaConfig, schemaDataOpt)
  }

  protected def extractTopic(topics: List[String]): String = {
    if (topics.length > 1) {
      throw new SerializationException(s"Topics list has more then one element: $topics.")
    }
    topics.head
  }
}

class ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroValueDeserializationSchemaFactory with ConfluentKafkaAvroDeserializerFactory {

  override protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[T]  =
    createDeserializer[T](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

  override protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[T] =
    createTypeInfo[T](kafkaConfig, schemaDataOpt)

}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, None, isKey = true)(keyClassTag)

  override protected def createKeyTypeInfo(kafkaConfig: KafkaConfig): TypeInformation[K] =
    createTypeInfo[K](kafkaConfig, None)(keyClassTag)

  override protected def createValueDeserializer(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)(valueClassTag)

  override protected def createValueTypeInfo(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[V] =
    createTypeInfo[V](kafkaConfig, schemaDataOpt)(valueClassTag)

}
