package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import org.apache.avro.specific.{SpecificRecord, SpecificRecordBase}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesAvroTypeInfo, LogicalTypesGenericRecordAvroTypeInfo}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.AvroSchemaDeterminer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{BasedOnVersionAvroSchemaDeterminer, SpecificRecordEmbeddedSchemaDeterminer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueDeserializationSchemaFactory, KafkaVersionAwareValueDeserializationSchemaFactory}

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory {

  protected def createDeserializer[T: ClassTag](schemaDeterminer: AvroSchemaDeterminer,
                                                schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                isKey: Boolean): (Deserializer[T], TypeInformation[T]) = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val isSpecificRecord = classOf[SpecificRecord].isAssignableFrom(clazz)

    val schemaOpt = schemaDeterminer.determineSchemaInRuntime.valueOr(exc => throw new SerializationException(s"Error determining Avro schema.", exc))

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

class ConfluentKafkaAvroDeserializationSchemaFactory[T: ClassTag](createSchemaDeterminer: (String, Option[Int]) => AvroSchemaDeterminer,
                                                                  schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[T], TypeInformation[T]) =
    createDeserializer[T](createSchemaDeterminer(extractTopic(topics), version), schemaRegistryClientFactory, kafkaConfig, isKey = false)

}

object ConfluentKafkaAvroDeserializationSchemaFactory {
  def apply[T: ClassTag](kafkaConfig: KafkaConfig, schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentKafkaAvroDeserializationSchemaFactory[T] = {
    def createSchemaDeterminer(topic: String, version: Option[Int]) = {
      // TODO: remove this implicit logic by passing schema (or schema determiner) in createValueDeserializer
      val clazz = classTag[T].runtimeClass
      if (classOf[SpecificRecord].isAssignableFrom(clazz)) {
        new SpecificRecordEmbeddedSchemaDeterminer(clazz.asInstanceOf[Class[_ <: SpecificRecord]])
      } else {
        new BasedOnVersionAvroSchemaDeterminer(() => schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig), topic, version)
      }
    }
    new ConfluentKafkaAvroDeserializationSchemaFactory(createSchemaDeterminer, schemaRegistryClientFactory)
  }
}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory[T](createSchemaDeterminer: (String, Option[Int]) => AvroSchemaDeterminer,
                                                                   schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaVersionAwareKeyValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[K], TypeInformation[K]) =
    createDeserializer[K](createSchemaDeterminer(extractTopic(topics), version), schemaRegistryClientFactory, kafkaConfig, isKey = true)(keyClassTag)

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[V], TypeInformation[V]) =
    createDeserializer[V](createSchemaDeterminer(extractTopic(topics), version), schemaRegistryClientFactory, kafkaConfig, isKey = false)(valueClassTag)
}
