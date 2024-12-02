package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedDeserializerFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ChainedSchemaIdFromMessageExtractor,
  ContentTypes,
  ContentTypesSchemas,
  SchemaId,
  SchemaRegistryClient,
  SchemaWithMetadata,
  StringSchemaId
}

import scala.reflect.ClassTag

class UniversalKafkaDeserializer[T](
    schemaRegistryClient: SchemaRegistryClient,
    kafkaConfig: KafkaConfig,
    schemaIdFromMessageExtractor: ChainedSchemaIdFromMessageExtractor,
    readerSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
    isKey: Boolean
) extends Deserializer[T] {

  private val schemaSupportDispatcher = UniversalSchemaSupportDispatcher(kafkaConfig)

  override def deserialize(topic: String, data: Array[Byte]): T = {
    throw new IllegalAccessException(
      s"Operation not supported. ${this.getClass.getSimpleName} requires kafka headers to perform deserialization."
    )
  }

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val writerSchemaId = schemaIdFromMessageExtractor
      .withFallbackSchemaId(readerSchemaDataOpt.flatMap(_.schemaIdOpt))
      .getSchemaId(headers, data, isKey)
      .getOrElse(throw MessageWithoutSchemaIdException)

    val schemaWithMetadata = {
      if (schemaRegistryClient.isTopicWithSchema(topic, kafkaConfig)) {
        schemaRegistryClient.getSchemaById(writerSchemaId.value)
      } else {
        writerSchemaId.value match {
          case StringSchemaId(value) =>
            if (value.equals(ContentTypes.PLAIN.toString)) {
              SchemaWithMetadata(ContentTypesSchemas.schemaForPlain, SchemaId.fromString(ContentTypes.PLAIN.toString))
            } else {
              SchemaWithMetadata(ContentTypesSchemas.schemaForJson, SchemaId.fromString(ContentTypes.JSON.toString))
            }
          case _ =>
            throw new IllegalStateException("Topic without schema should have ContentType Json or Plain, was neither")
        }
      }
    }

    val writerSchema = schemaWithMetadata.schema
    readerSchemaDataOpt
      .map(_.schema.schemaType())
      .foreach(readerSchemaType => {
        if (readerSchemaType != writerSchema.schemaType())
          throw new MismatchReaderWriterSchemaException(
            readerSchemaType,
            writerSchema.schemaType()
          ) // TODO: test this case when supporting json schema
      })

    val writerSchemaData =
      new RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](writerSchema), Some(writerSchemaId.value))

    schemaSupportDispatcher
      .forSchemaType(writerSchema.schemaType())
      .payloadDeserializer
      .deserialize(readerSchemaDataOpt, writerSchemaData, writerSchemaId.buffer)
      .asInstanceOf[T]
  }

}

object MessageWithoutSchemaIdException
    extends IllegalArgumentException("Missing schemaId in kafka headers, in payload, and no fallback provided")

class MismatchReaderWriterSchemaException(expectedType: String, actualType: String)
    extends IllegalArgumentException(
      s"Expecting schema of type $expectedType. but got payload with $actualType schema type"
    )

class UniversalKafkaDeserializerFactory(
    createSchemaIdFromMessageExtractor: SchemaRegistryClient => ChainedSchemaIdFromMessageExtractor
) extends SchemaRegistryBasedDeserializerFactory {

  def createDeserializer[T: ClassTag](
      schemaRegistryClient: SchemaRegistryClient,
      kafkaConfig: KafkaConfig,
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      isKey: Boolean
  ): Deserializer[T] = {
    new UniversalKafkaDeserializer[T](
      schemaRegistryClient,
      kafkaConfig,
      createSchemaIdFromMessageExtractor(schemaRegistryClient),
      schemaDataOpt,
      isKey
    )
  }

}
