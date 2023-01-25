package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaProvider, AvroSchemaUtils}
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import org.apache.kafka.common.errors.SerializationException
import org.everit.json.schema.{Schema => EveritSchema}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.StringForcingDatumReaderProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, OpenAPIJsonSchema}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaWithMetadata}

import java.io.{ByteArrayOutputStream, DataOutputStream, OutputStream}
import java.nio.ByteBuffer
import java.util

object ConfluentUtils extends LazyLogging {

  private final val ValueSubjectPattern = "(.*)-value".r

  final val SchemaProvider = new AvroSchemaProvider()
  final val MagicByte = 0
  final val IdSize = 4

  final val HeaderSize = 1 + IdSize // magic byte + schemaId (4 bytes int)

  def topicSubject(topic: String, isKey: Boolean): String =
    if (isKey) keySubject(topic) else valueSubject(topic)

  def keySubject(topic: String): String =
    topic + "-key"

  def valueSubject(topic: String): String =
    topic + "-value"

  def topicFromSubject: PartialFunction[String, String] = {
    case ValueSubjectPattern(value) => value
  }

  def toSchemaWithMetadata(schemaMetadata: SchemaMetadata, config: SchemaRegistryClientKafkaConfig): SchemaWithMetadata = {
    val confluentParsedSchema = schemaMetadata.getSchemaType match {
      case "AVRO" => new AvroSchema(schemaMetadata.getSchema)
      case "JSON" => OpenAPIJsonSchema(schemaMetadata.getSchema)
      case other => throw new IllegalArgumentException(s"Not supported schema type: $other")
    }
    val adjustedSchema = AvroUtils.adjustParsedSchema(confluentParsedSchema, config)
    SchemaWithMetadata(adjustedSchema, SchemaId.fromInt(schemaMetadata.getId))
  }

  def convertToAvroSchema(schema: Schema, version: Option[Int] = None): AvroSchema =
    version.map(new AvroSchema(schema, _)).getOrElse(new AvroSchema(schema))

  def convertToJsonSchema(schema: EveritSchema, version: Option[Int] = None): JsonSchema =
    version.map(new JsonSchema(schema, _)).getOrElse(new JsonSchema(schema))

  def extractSchema(parsedSchema: ParsedSchema): Schema =
    parsedSchema.rawSchema().asInstanceOf[Schema]

  def parsePayloadToByteBuffer(payload: Array[Byte]): Validated[IllegalArgumentException, ByteBuffer] = {
    val buffer = ByteBuffer.wrap(payload)
    if (buffer.array().isEmpty)
      // Here parsed payload is an empty buffer. In that case buffer.get below raises "java.nio.BufferUnderflowException".
      // This usually happens when the content of key or value is null.
      Validated.invalid(new IllegalArgumentException("Buffer is empty"))
    else if (buffer.get != MagicByte)
      Validated.invalid(new IllegalArgumentException("Unknown magic byte!"))
    else
      Validated.valid(buffer)
  }

  def readIdAndGetBuffer(bytes: Array[Byte]): Validated[IllegalArgumentException, (SchemaId, ByteBuffer)] = ConfluentUtils
    .parsePayloadToByteBuffer(bytes)
    .map(b => (SchemaId.fromInt(b.getInt()), b))

  def readIdAndGetBufferUnsafe(bytes: Array[Byte]): (SchemaId, ByteBuffer) = readIdAndGetBuffer(bytes)
    .valueOr(exc => throw new SerializationException(exc.getMessage, exc))

  def readId(bytes: Array[Byte]): SchemaId = readIdAndGetBufferUnsafe(bytes)._1

  /**
    * Based on serializeImpl from [[io.confluent.kafka.serializers.AbstractKafkaAvroSerializer]]
    */
  def serializeContainerToBytesArray(container: GenericContainer, schemaId: SchemaId): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    try {
      writeSchemaId(schemaId, output)
      AvroUtils.serializeContainerToBytesArray(container, output)
      output.toByteArray
    } finally {
      output.close()
    }
  }

  def writeSchemaId(schemaId: SchemaId, stream: OutputStream): Unit = {
    val dos = new DataOutputStream(stream)
    dos.write(MagicByte)
    dos.writeInt(schemaId.asInt)
  }

  def deserializeSchemaIdAndData[T](payload: Array[Byte], readerWriterSchema: Schema): (SchemaId, T) = {
    val schemaId = ConfluentUtils.readId(payload)
    val data = AvroUtils.deserialize[T](payload, readerWriterSchema, HeaderSize)
    (schemaId, data)
  }

}
