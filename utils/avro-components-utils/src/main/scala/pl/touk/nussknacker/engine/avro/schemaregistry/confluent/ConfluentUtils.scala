package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaProvider, AvroSchemaUtils}
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericData, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, Encoder, EncoderFactory}
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReaderProvider

import java.io.{ByteArrayOutputStream, DataOutputStream, OutputStream}
import java.nio.ByteBuffer

object ConfluentUtils extends LazyLogging {

  private final val ValueSubjectPattern = "(.*)-value".r

  final val SchemaProvider = new AvroSchemaProvider()
  final val MagicByte = 0

  final val HeaderSize = 1 + 4 // magic byte + schemaId (4 bytes int)

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

  def readId(bytes: Array[Byte]): Int =
    ConfluentUtils
      .parsePayloadToByteBuffer(bytes)
      .valueOr(exc => throw new SerializationException(exc.getMessage, exc))
      .getInt

  /**
    * Based on serializeImpl from [[io.confluent.kafka.serializers.AbstractKafkaAvroSerializer]]
    */
  def serializeDataToBytesArray(data: Any, schemaId: Int): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    writeSchemaId(schemaId, output)

    data match {
      case v: Array[Byte] =>
        output.write(v)
      case v =>
        val schema = data match {
          case v: GenericContainer => v.getSchema
          case _ => AvroSchemaUtils.getSchema(data)
        }

        val writer = data match {
          case _: SpecificRecord =>
           new SpecificDatumWriter[Any](schema, AvroUtils.specificData)
          case _ =>
            new GenericDatumWriter[Any](schema, AvroUtils.genericData)
        }

        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        writer.write(v, encoder)
        encoder.flush()
    }

    val bytes = output.toByteArray
    output.close()
    bytes
  }

  private def writeSchemaId(schemaId: Int, stream: OutputStream): Unit = {
    val dos = new DataOutputStream(stream)
    dos.write(MagicByte)
    dos.writeInt(schemaId)
  }

  def deserializeSchemaIdAndData[T](payload: Array[Byte], readerWriterSchema: Schema): (Int, T) = {
    val schemaId = ConfluentUtils.readId(payload)
    val decoder = DecoderFactory.get().binaryDecoder(payload, ConfluentUtils.HeaderSize, payload.length - ConfluentUtils.HeaderSize, null)
    val reader = StringForcingDatumReaderProvider.genericDatumReader[T](readerWriterSchema, readerWriterSchema, AvroUtils.genericData)
    (schemaId, reader.read(null.asInstanceOf[T], decoder))
  }

}
