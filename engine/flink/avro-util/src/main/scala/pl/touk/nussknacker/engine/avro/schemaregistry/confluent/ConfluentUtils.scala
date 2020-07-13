package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import java.nio.ByteBuffer

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaProvider}
import org.apache.avro.Schema
import org.apache.kafka.common.errors.SerializationException

object ConfluentUtils {

  private final val ValueSubjectPattern = "(.*)-value".r

  final val SchemaProvider = new AvroSchemaProvider()
  final val MagicByte = 0

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
    if (buffer.get != MagicByte)
      Validated.invalid(new IllegalArgumentException("Unknown magic byte!"))
    else
      Validated.valid(buffer)
  }

  def readId(bytes: Array[Byte]): Int =
    ConfluentUtils
      .parsePayloadToByteBuffer(bytes)
      .valueOr(exc => throw new SerializationException(exc.getMessage, exc))
      .getInt
}
