package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.serializers._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchemaFactoryBase, KafkaKeyValueDeserializationSchemaFactoryBase}

class AvroDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                           useSpecificAvroReader: Boolean)
  extends KafkaDeserializationSchemaFactoryBase[T]  {

  import collection.convert.decorateAsJava._

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[T] = {
    // TODO: this client is never destroyed and it is potential leak of resources
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val deserializer = new KafkaAvroDeserializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) +
      ("specific.avro.reader" -> useSpecificAvroReader)
    deserializer.configure(props.asJava, false)
    deserializer.asInstanceOf[Deserializer[T]]
  }

}

abstract class AvroKeyValueDeserializationSchemaFactory[T: TypeInformation](schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                                            useSpecificAvroReader: Boolean)
  extends KafkaKeyValueDeserializationSchemaFactoryBase[T]  {

  import collection.convert.decorateAsJava._

  override protected def createKeyDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[K] = {
    createDeserializer(kafkaConfig, isKey = true).asInstanceOf[Deserializer[K]]
  }

  override protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[V] = {
    createDeserializer(kafkaConfig, isKey = false).asInstanceOf[Deserializer[V]]
  }

  private def createDeserializer(kafkaConfig: KafkaConfig, isKey: Boolean) = {
    // TODO: this client is never destroyed and it is potential leak of resources
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    val deserializer = new KafkaAvroDeserializer(schemaRegistryClient)
    val props = kafkaConfig.kafkaProperties.getOrElse(Map.empty) +
      ("specific.avro.reader" -> useSpecificAvroReader)
    deserializer.configure(props.asJava, isKey)
    deserializer
  }

}