package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.avro.specific.SpecificRecordBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesAvroTypeInfo, LogicalTypesGenericRecordAvroTypeInfo}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroKeyValueDeserializationSchemaFactory, KafkaAvroValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaOpt: Option[RuntimeSchemaData],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaOpt.orNull, schemaRegistryClient, isKey = isKey, AvroUtils.isSpecificRecord[T])
  }

  protected def createTypeInfo[T: ClassTag](schemaOpt: Option[RuntimeSchemaData]): TypeInformation[T] = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val isSpecificRecord = AvroUtils.isSpecificRecord[T]

    schemaOpt match {
      case Some(schema) if !isSpecificRecord =>
        new LogicalTypesGenericRecordAvroTypeInfo(schema.schema).asInstanceOf[TypeInformation[T]]
      case _ if isSpecificRecord => // For specific records we ignoring version because we have exact schema inside class
        new LogicalTypesAvroTypeInfo(clazz.asInstanceOf[Class[_ <: SpecificRecordBase]]).asInstanceOf[TypeInformation[T]]
      case _ =>
        // Is type info is correct for non-specific-record case? We can't do too much more without schema.
        TypeInformation.of(clazz)
    }
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

  override protected def createValueDeserializer[T: ClassTag](schemaOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[T]  =
    createDeserializer[T](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)

  override protected def createValueTypeInfo[T: ClassTag](schemaOpt: Option[RuntimeSchemaData]): TypeInformation[T] =
    createTypeInfo[T](schemaOpt)

}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, None, isKey = true)(keyClassTag)

  override protected def createKeyTypeInfo(): TypeInformation[K] =
    createTypeInfo[K](None)(keyClassTag)

  override protected def createValueDeserializer(schemaOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)(valueClassTag)

  override protected def createValueTypeInfo(schemaOpt: Option[RuntimeSchemaData]): TypeInformation[V] =
    createTypeInfo[V](schemaOpt)(valueClassTag)

}
