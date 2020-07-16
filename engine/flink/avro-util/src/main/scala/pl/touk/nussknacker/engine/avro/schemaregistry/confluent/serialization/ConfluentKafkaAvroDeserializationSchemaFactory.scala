package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.avro.Schema
import org.apache.avro.specific.{SpecificRecord, SpecificRecordBase}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesAvroTypeInfo, LogicalTypesGenericRecordAvroTypeInfo}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroKeyValueDeserializationSchemaFactory, KafkaAvroValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaOpt: Option[Schema],
                                                isKey: Boolean): (Deserializer[T], TypeInformation[T]) = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val isSpecificRecord = classOf[SpecificRecord].isAssignableFrom(clazz)

    val deserializer = new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schemaOpt.orNull, schemaRegistryClient, isKey = isKey, isSpecificRecord)

    val typeInformation = schemaOpt match {
      case Some(schema) if !isSpecificRecord =>
        new LogicalTypesGenericRecordAvroTypeInfo(schema).asInstanceOf[TypeInformation[T]]
      case _ if isSpecificRecord => // For specific records we ignoring version because we have exact schema inside class
        new LogicalTypesAvroTypeInfo(clazz.asInstanceOf[Class[_ <: SpecificRecordBase]]).asInstanceOf[TypeInformation[T]]
      case _ =>
        // Is type info is correct for non-specific-record case? We can't do too much more without schema.
        TypeInformation.of(clazz)
    }

    (deserializer, typeInformation)
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

  override protected def createValueDeserializer[T: ClassTag](schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): (Deserializer[T], TypeInformation[T]) =
    createDeserializer[T](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)

}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaAvroKeyValueDeserializationSchemaFactory with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): (Deserializer[K], TypeInformation[K]) =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, None, isKey = true)(keyClassTag)

  override protected def createValueDeserializer(schemaOpt: Option[Schema], kafkaConfig: KafkaConfig): (Deserializer[V], TypeInformation[V]) =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaOpt, isKey = false)(valueClassTag)

}
