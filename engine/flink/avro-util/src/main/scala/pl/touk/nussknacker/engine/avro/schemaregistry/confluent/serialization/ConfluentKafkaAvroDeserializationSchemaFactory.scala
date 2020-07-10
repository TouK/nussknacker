package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.serializers._
import org.apache.avro.specific.{SpecificRecord, SpecificRecordBase}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{AvroTypeInfo, GenericRecordAvroTypeInfo}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.SchemaDeterminingStrategy.SchemaDeterminingStrategy
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueDeserializationSchemaFactory, KafkaVersionAwareValueDeserializationSchemaFactory}

import scala.reflect._

trait ConfluentKafkaAvroDeserializerFactory extends ConfluentKafkaAvroSerializationMixin {
  import collection.JavaConverters._

  protected def createDeserializer[T: ClassTag](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                                topic: String,
                                                version: Option[Int],
                                                schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                isKey: Boolean): (Deserializer[T], TypeInformation[T]) = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]

    val (deserializer, typeInformation) = schemaDeterminingStrategy match {
      case SchemaDeterminingStrategy.FromSubjectVersion =>
        val schema = fetchSchema(schemaRegistryClient, topic, version, isKey = isKey)
        val d = new ConfluentKafkaAvroDeserializer(schema, schemaRegistryClient, isKey = isKey)
        val typeInfo = determineTypeInfo(clazz, new GenericRecordAvroTypeInfo(schema).asInstanceOf[TypeInformation[T]])
        (d, typeInfo)
      case SchemaDeterminingStrategy.FromRecord =>
        // Is type info is correct for non-specific-record case? We can't do too much more without schema.
        val typeInfo = determineTypeInfo(clazz, TypeInformation.of(clazz))
        (new KafkaAvroDeserializer(schemaRegistryClient.client), typeInfo)
    }

    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> classOf[SpecificRecord].isAssignableFrom(clazz)
    )

    deserializer.configure(props.asJava, isKey)
    (deserializer.asInstanceOf[Deserializer[T]], typeInformation)
  }

  // See Flink's AvroDeserializationSchema
  private def determineTypeInfo[T](clazz: Class[T], nonSpecificRecordTypeInfo: => TypeInformation[T]) = {
    if (classOf[SpecificRecord].isAssignableFrom(clazz))
      new AvroTypeInfo(clazz.asInstanceOf[Class[_ <: SpecificRecordBase]]).asInstanceOf[TypeInformation[T]]
    else
      nonSpecificRecordTypeInfo
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
