package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializationMode.ConfluentKafkaAvroDeserializationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.serializer.{ConfluentKafkaAvroDeserializer, ConfluentStaticKafkaAvroDeserializer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareKeyValueDeserializationSchemaFactory, KafkaVersionAwareValueDeserializationSchemaFactory}

trait ConfluentKafkaAvroDeserializerFactory {
  import collection.JavaConverters._

  protected def createDeserializer[T](mode: ConfluentKafkaAvroDeserializationMode,
                                      topic: String,
                                      version: Option[Int],
                                      schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      kafkaConfig: KafkaConfig,
                                      isKey: Boolean,
                                      useSpecificAvroReader: Boolean): Deserializer[T] = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

    val deserializer = mode match {
      case ConfluentKafkaAvroDeserializationMode.STATIC =>
        ConfluentStaticKafkaAvroDeserializer(schemaRegistryClient, topic, version, isKey = isKey)
      case ConfluentKafkaAvroDeserializationMode.FRESH =>
        new ConfluentKafkaAvroDeserializer(schemaRegistryClient, topic, version, isKey = isKey)
      case _ =>
        new KafkaAvroDeserializer(schemaRegistryClient.client)
    }

    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) + (
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> useSpecificAvroReader
    )

    deserializer.configure(props.asJava, isKey)
    deserializer.asInstanceOf[Deserializer[T]]
  }
}

class ConfluentKafkaAvroDeserializationSchemaFactory[T: TypeInformation](mode: ConfluentKafkaAvroDeserializationMode,
                                                                         schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                                         useSpecificAvroReader: Boolean)
  extends KafkaVersionAwareValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[T] =
    createDeserializer[T](mode, topics.head, version, schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}

object ConfluentKafkaAvroDeserializationSchemaFactory {
  def apply[T: TypeInformation](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, useSpecificAvroReader: Boolean): ConfluentKafkaAvroDeserializationSchemaFactory[T] =
    new ConfluentKafkaAvroDeserializationSchemaFactory(ConfluentKafkaAvroDeserializationMode.STATIC, schemaRegistryClientFactory, useSpecificAvroReader)
}

abstract class ConfluentKeyValueKafkaAvroDeserializationFactory[T: TypeInformation](mode: ConfluentKafkaAvroDeserializationMode,
                                                                                    schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                                                    useSpecificAvroReader: Boolean)
  extends KafkaVersionAwareKeyValueDeserializationSchemaFactory[T] with ConfluentKafkaAvroDeserializerFactory {

  override protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](mode, topics.head, version, schemaRegistryClientFactory, kafkaConfig, isKey = true, useSpecificAvroReader)

  override protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](mode, topics.head, version, schemaRegistryClientFactory, kafkaConfig, isKey = false, useSpecificAvroReader)
}

object ConfluentKafkaAvroDeserializationMode extends Enumeration {
  type ConfluentKafkaAvroDeserializationMode = Value
  val NORMAL, STATIC, FRESH = Value
}
