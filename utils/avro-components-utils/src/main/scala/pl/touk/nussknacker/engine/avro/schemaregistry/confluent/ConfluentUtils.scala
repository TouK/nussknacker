package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaProvider, AvroSchemaUtils}
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import org.apache.kafka.common.errors.SerializationException
import org.everit.json.schema.{Schema => EveritSchema}
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReaderProvider

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

  def readIdAndGetBuffer(bytes: Array[Byte]): (Int, ByteBuffer) = ConfluentUtils
    .parsePayloadToByteBuffer(bytes)
    .map(b => (b.getInt(), b))
    .valueOr(exc => throw new SerializationException(exc.getMessage, exc))

  def readId(bytes: Array[Byte]): Int = readIdAndGetBuffer(bytes)._1

  /**
    * Based on serializeImpl from [[io.confluent.kafka.serializers.AbstractKafkaAvroSerializer]]
    */
  def serializeContainerToBytesArray(container: GenericContainer, schemaId: Int): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    writeSchemaId(schemaId, output)

    val data = container match {
      case non: NonRecordContainer => non.getValue
      case any => any
    }

    data match {
      case v: ByteBuffer =>
        output.write(v.array())
      case v: Array[Byte] =>
        output.write(v)
      case v =>
        val writer = data match {
          case _: SpecificRecord =>
            new SpecificDatumWriter[Any](container.getSchema, AvroUtils.specificData)
          case _ =>
            new GenericDatumWriter[Any](container.getSchema, AvroUtils.genericData)
        }

        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        writer.write(v, encoder)
        encoder.flush()
    }

    val bytes = output.toByteArray
    output.close()
    bytes
  }

  def writeSchemaId(schemaId: Int, stream: OutputStream): Unit = {
    val dos = new DataOutputStream(stream)
    dos.write(MagicByte)
    dos.writeInt(schemaId)
  }

  def deserializeSchemaIdAndData[T](payload: Array[Byte], readerWriterSchema: Schema): (Int, T) = {
    val schemaId = ConfluentUtils.readId(payload)

    val data = if (readerWriterSchema.getType.equals(Schema.Type.BYTES)) {
      util.Arrays.copyOfRange(payload, ConfluentUtils.HeaderSize, payload.length).asInstanceOf[T]
    } else {
      val decoder = DecoderFactory.get().binaryDecoder(payload, ConfluentUtils.HeaderSize, payload.length - ConfluentUtils.HeaderSize, null)
      val reader = StringForcingDatumReaderProvider.genericDatumReader[T](readerWriterSchema, readerWriterSchema, AvroUtils.genericData)
      reader.read(null.asInstanceOf[T], decoder)
    }

    (schemaId, data)
  }

}
