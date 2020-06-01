package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.SchemaDeterminingStrategy.SchemaDeterminingStrategy
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer.ConfluentKafkaAvroDeserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueDeserializationSchemaFactory, KafkaVersionAwareValueDeserializationSchemaFactory}

trait ConfluentKafkaAvroDeserializerFactory {
  import collection.JavaConverters._

  protected def createDeserializer[T](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                      topic: String,
                                      version: Option[Int],
                                      schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      kafkaConfig: KafkaConfig,
                                      isKey: Boolean,
                                      useSpecificAvroReader: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val deserializer = schemaDeterminingStrategy match {
      case SchemaDeterminingStrategy.FromSubjectVersion =>
        ConfluentKafkaAvroDeserializer(schemaRegistryClient, topic, version, isKey = isKey)
      case SchemaDeterminingStrategy.FromRecord =>
        new KafkaAvroDeserializer(schemaRegistryClient.client)
    }

    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )

    deserializer.configure(props.asJava, isKey)
    deserializer.asInstanceOf[Deserializer[T]]
  }

  protected def extractTopic(topics: List[String]): String = {
    if (topics.length > 1) {
      throw new SerializationException(s"Topics list has more then one element: $topics.")
    }

    topics.head
  }
}

class ConfluentKafkaAvroDeserializationSchemaFactory[T: TypeInformation](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                                                         schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                                         useSpecificAvroReader: Boolean)
  extends KafkaVersionAwareValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer[T](schemaDeterminingStrategy, extractTopic(topics), version, schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}

object ConfluentKafkaAvroDeserializationSchemaFactory {
  def apply[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean): ConfluentKafkaAvroDeserializationSchemaFactory[T] =
    new ConfluentKafkaAvroDeserializationSchemaFactory(SchemaDeterminingStrategy.FromSubjectVersion, schemaRegistryClientFactory, useSpecificAvroReader)
}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory[T: TypeInformation](schemaDeterminingStrategy: SchemaDeterminingStrategy,
                                                                                    schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                                                    useSpecificAvroReader: Boolean)
  extends KafkaVersionAwareKeyValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaDeterminingStrategy, extractTopic(topics), version, schemaRegistryClientFactory, kafkaConfig, isKey = true, useSpecificAvroReader)

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaDeterminingStrategy, extractTopic(topics), version, schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}

/**
  * FromSubjectVersion - Deserializer always fetches schema by given subject and schema version as parameters
  * FromRecord - Standard Confluent Deserializer, it always fetches schema by message schema id
  */
object SchemaDeterminingStrategy extends Enumeration {
  type SchemaDeterminingStrategy = Value
  val FromRecord, FromSubjectVersion = Value
}
