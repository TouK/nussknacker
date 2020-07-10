package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.avro.specific.{SpecificRecord, SpecificRecordBase}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesAvroFactory, LogicalTypesAvroTypeInfo, LogicalTypesGenericRecordAvroTypeInfo}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.SchemaDeterminingStrategy.SchemaDeterminingStrategy
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueDeserializationSchemaFactory, KafkaVersionAwareValueDeserializationSchemaFactory}

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory extends ConfluentKafkaAvroSerializationMixin {

  protected def createDeserializer[T: ClassTag](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                                topic: String,
                                                version: Option[Int],
                                                schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                isKey: Boolean): (Deserializer[T], TypeInformation[T]) = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val isSpecificRecord = classOf[SpecificRecord].isAssignableFrom(clazz)

    val schema = schemaDeterminingStrategy match {
      case SchemaDeterminingStrategy.FromSubjectVersion if !isSpecificRecord =>
        fetchSchema(schemaRegistryClient, topic, version, isKey = isKey)
      case _ if isSpecificRecord =>
        LogicalTypesAvroFactory.extractAvroSpecificSchema(clazz, AvroUtils.specificData)
      case _ =>
        null
    }

    val deserializer = new ConfluentKafkaAvroDeserializer[T](kafkaConfig, schema, schemaRegistryClient, isKey = isKey, isSpecificRecord)

    val typeInformation = schemaDeterminingStrategy match {
      case SchemaDeterminingStrategy.FromSubjectVersion if !isSpecificRecord =>
        new LogicalTypesGenericRecordAvroTypeInfo(schema).asInstanceOf[TypeInformation[T]]
      case _ if isSpecificRecord => // For specific records we ingoring version because we have exact schema inside class
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

class ConfluentKafkaAvroDeserializationSchemaFactory[T: ClassTag](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                                                  schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[T], TypeInformation[T]) =
    createDeserializer[T](schemaDeterminingStrategy, extractTopic(topics), version, schemaRegistryClientFactory, kafkaConfig, isKey = false)

}

object ConfluentKafkaAvroDeserializationSchemaFactory {
  def apply[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentKafkaAvroDeserializationSchemaFactory[T] =
    new ConfluentKafkaAvroDeserializationSchemaFactory(SchemaDeterminingStrategy.FromSubjectVersion, schemaRegistryClientFactory)
}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory[T](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                                                   schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareKeyValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[K], TypeInformation[K]) =
    createDeserializer[K](schemaDeterminingStrategy, extractTopic(topics), version, schemaRegistryClientFactory, kafkaConfig, isKey = true)(keyClassTag)

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[V], TypeInformation[V]) =
    createDeserializer[V](schemaDeterminingStrategy, extractTopic(topics), version, schemaRegistryClientFactory, kafkaConfig, isKey = false)(valueClassTag)
}
