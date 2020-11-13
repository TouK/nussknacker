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
                                                schemaDataOpt: Option[RuntimeSchemaData],
                                                isKey: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaDataOpt.orNull, schemaRegistryClient, isKey = isKey, AvroUtils.isSpecificRecord[T])
  }

  protected def createTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData]): TypeInformation[T] = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val isSpecificRecord = AvroUtils.isSpecificRecord[T]

    schemaDataOpt match {
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

  override protected def createValueDeserializer[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[T]  =
    createDeserializer[T](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)

  override protected def createValueTypeInfo[T: ClassTag](schemaDataOpt: Option[RuntimeSchemaData]): TypeInformation[T] =
    createTypeInfo[T](schemaDataOpt)

}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, None, isKey = true)(keyClassTag)

  override protected def createKeyTypeInfo(): TypeInformation[K] =
    createTypeInfo[K](None)(keyClassTag)

  override protected def createValueDeserializer(schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)(valueClassTag)

  override protected def createValueTypeInfo(schemaDataOpt: Option[RuntimeSchemaData]): TypeInformation[V] =
    createTypeInfo[V](schemaDataOpt)(valueClassTag)

}
