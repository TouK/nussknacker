package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.DecoderFactory
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroRecordDeserializer, DatumReaderWriterMixin}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

trait UniversalSchemaPayloadDeserializer {

  def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any

}

object AvroPayloadDeserializer {
  val instance = new AvroPayloadDeserializer(DecoderFactory.get())
}

// This implementation is based on Confluent's one but currently deosn't use any Confluent specific things
class AvroPayloadDeserializer(
    decoderFactory: DecoderFactory
) extends DatumReaderWriterMixin
    with UniversalSchemaPayloadDeserializer {

  private val recordDeserializer = new AvroRecordDeserializer(decoderFactory)

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): AnyRef = {
    val avroExpectedSchemaData = expectedSchemaData.asInstanceOf[Option[RuntimeSchemaData[AvroSchema]]]
    val avroWriterSchemaData   = writerSchemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
    val readerSchemaData       = avroExpectedSchemaData.getOrElse(avroWriterSchemaData)
    val reader = createDatumReader(avroWriterSchemaData.schema.rawSchema(), readerSchemaData.schema.rawSchema())
    recordDeserializer.deserializeRecord(readerSchemaData.schema.rawSchema(), reader, buffer)
  }

}

object JsonPayloadDeserializer extends UniversalSchemaPayloadDeserializer {

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): AnyRef = {
    val avroSchema = writerSchemaData.schema.rawSchema().asInstanceOf[Schema]
    val bytes      = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    JsonPayloadToAvroConverter.convert(bytes, avroSchema)
  }

}

object JsonSchemaPayloadDeserializer extends UniversalSchemaPayloadDeserializer {

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any = {
    val jsonSchema =
      expectedSchemaData.getOrElse(writerSchemaData).asInstanceOf[RuntimeSchemaData[OpenAPIJsonSchema]].schema
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    jsonSchema.deserializer.deserialize(bytes)
  }

}

object PlainTextPayloadDeserializer extends UniversalSchemaPayloadDeserializer {

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    new String(bytes, StandardCharsets.UTF_8)
  }

}

class JsonTypingResultPayloadDeserializer(typingResult: TypingResult) extends UniversalSchemaPayloadDeserializer {

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)

    CirceUtil
      .decodeJson[Json](bytes)
      .flatMap(json =>
        FromJsonTypingResultBasedDecoder
          .decodeValue(typingResult, json.hcursor)
      )
      .fold(ex => throw new PayloadDeserializationException(ex), identity)
  }

}

final class PayloadDeserializationException(cause: Exception)
    extends RuntimeException(s"Exception during payload deserialization: ${cause.getMessage}", cause)
