package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.UniversalSchemaSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

import scala.reflect.ClassTag

class MismatchReaderWriterSchemaException(expectedType: String, actualType: String) extends IllegalArgumentException(s"Expecting schema of type $expectedType. but got payload with $actualType schema type")

class ConfluentUniversalKafkaDeserializer[T](override val schemaRegistryClient: ConfluentSchemaRegistryClient,
                                             readerSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                             isKey: Boolean) extends Deserializer[T] with UniversalSchemaIdFromMessageExtractor {

  override def deserialize(topic: String, data: Array[Byte]): T = {
    throw new IllegalAccessException(s"Operation not supported. ${this.getClass.getSimpleName} requires kafka headers to perform deserialization.")
  }

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val writerSchemaId = getSchemaId(headers, data, isKey, fallback = readerSchemaDataOpt.flatMap(_.schemaIdOpt))
    val writerSchema = schemaRegistryClient.getSchemaById(writerSchemaId.value).schema

    readerSchemaDataOpt.map(_.schema.schemaType()).foreach(readerSchemaType => {
      if (readerSchemaType != writerSchema.schemaType())
        throw new MismatchReaderWriterSchemaException(readerSchemaType, writerSchema.schemaType()) //todo: test this case when supporting json schema
    })

    val writerSchemaData = new RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](writerSchema), Some(writerSchemaId.value))

    UniversalSchemaSupport.forSchemaType(writerSchema.schemaType())
      .payloadDeserializer
      .deserialize(readerSchemaDataOpt, writerSchemaData, writerSchemaId.buffer, writerSchemaId.bufferStartPosition)
      .asInstanceOf[T]
  }
}

trait ConfluentUniversalKafkaDeserializerFactory extends LazyLogging {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                isKey: Boolean): Deserializer[T] = {

    val schemaRegistryClient: ConfluentSchemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    new ConfluentUniversalKafkaDeserializer[T](schemaRegistryClient, schemaDataOpt, isKey)
  }

}

class ConfluentKeyValueUniversalKafkaDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory with ConfluentUniversalKafkaDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)
}