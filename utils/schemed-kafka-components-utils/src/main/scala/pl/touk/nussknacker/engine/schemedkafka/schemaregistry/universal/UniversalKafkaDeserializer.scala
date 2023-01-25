package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedDeserializerFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ChainedSchemaIdFromMessageExtractor, SchemaRegistryClient}

import scala.reflect.ClassTag

class UniversalKafkaDeserializer[T](schemaRegistryClient: SchemaRegistryClient,
                                    kafkaConfig: KafkaConfig,
                                    schemaIdFromMessageExtractor: ChainedSchemaIdFromMessageExtractor,
                                    readerSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                    isKey: Boolean) extends Deserializer[T] {

  override def deserialize(topic: String, data: Array[Byte]): T = {
    throw new IllegalAccessException(s"Operation not supported. ${this.getClass.getSimpleName} requires kafka headers to perform deserialization.")
  }

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val writerSchemaId = schemaIdFromMessageExtractor.withFallbackSchemaId(readerSchemaDataOpt.flatMap(_.schemaIdOpt))
      .getSchemaId(headers, data, isKey)
      .getOrElse(throw MessageWithoutSchemaIdException)
    val writerSchema = schemaRegistryClient.getSchemaById(writerSchemaId.value).schema

    readerSchemaDataOpt.map(_.schema.schemaType()).foreach(readerSchemaType => {
      if (readerSchemaType != writerSchema.schemaType())
        throw new MismatchReaderWriterSchemaException(readerSchemaType, writerSchema.schemaType()) //todo: test this case when supporting json schema
    })

    val writerSchemaData = new RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](writerSchema), Some(writerSchemaId.value))

    UniversalSchemaSupport.forSchemaType(writerSchema.schemaType())
      .payloadDeserializer(kafkaConfig)
      .deserialize(readerSchemaDataOpt, writerSchemaData, writerSchemaId.buffer, writerSchemaId.bufferStartPosition)
      .asInstanceOf[T]
  }
}

object MessageWithoutSchemaIdException extends IllegalArgumentException("Missing schemaId in kafka headers, in payload, and no fallback provided")

class MismatchReaderWriterSchemaException(expectedType: String, actualType: String) extends IllegalArgumentException(s"Expecting schema of type $expectedType. but got payload with $actualType schema type")

class UniversalKafkaDeserializerFactory(schemaIdFromMessageExtractor: ChainedSchemaIdFromMessageExtractor)
  extends SchemaRegistryBasedDeserializerFactory {

  def createDeserializer[T: ClassTag](schemaRegistryClient: SchemaRegistryClient,
                                      kafkaConfig: KafkaConfig,
                                      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                      isKey: Boolean): Deserializer[T] = {
    new UniversalKafkaDeserializer[T](schemaRegistryClient, kafkaConfig, schemaIdFromMessageExtractor, schemaDataOpt, isKey)
  }

}