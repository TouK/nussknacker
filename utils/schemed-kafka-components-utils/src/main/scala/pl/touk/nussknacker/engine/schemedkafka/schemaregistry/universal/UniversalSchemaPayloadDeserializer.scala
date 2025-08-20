package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.DecoderFactory
import org.json.JSONTokener
import pl.touk.nussknacker.engine.api.json.decoders.{FromJsonSimpleDecoder, FromJsonTypingResultBasedDecoder}
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroRecordDeserializer, DatumReaderWriterMixin}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.util.json.JsonSchemaUtils

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
  def apply(config: KafkaConfig) =
    new AvroPayloadDeserializer(GenericRecordSchemaIdSerializationSupport(config), DecoderFactory.get())
}

// This implementation is based on Confluent's one but currently deosn't use any Confluent specific things
class AvroPayloadDeserializer(
    genericRecordSchemaIdSerializationSupport: GenericRecordSchemaIdSerializationSupport,
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
    val result = recordDeserializer.deserializeRecord(readerSchemaData.schema.rawSchema(), reader, buffer)
    genericRecordSchemaIdSerializationSupport.wrapWithRecordWithSchemaIdIfNeeded(result, readerSchemaData)
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
    JsonPayloadToAvroConverter.convert(buffer.array(), avroSchema)
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

class JsonTypingResultPayloadDeserializer(typingResult: TypingResult) extends UniversalSchemaPayloadDeserializer {
  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any = {
    val jsonSchema =
      expectedSchemaData.getOrElse(writerSchemaData).asInstanceOf[RuntimeSchemaData[OpenAPIJsonSchema]].schema

    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    val string = new String(bytes, StandardCharsets.UTF_8)

    val inputJson = new JSONTokener(string).nextValue()
    val validatedJson = jsonSchema
      .rawSchema()
      .validateData(inputJson)
      .valueOr(errorMsg => throw CustomNodeValidationException(errorMsg, None))

    val circeJson = JsonSchemaUtils.jsonToCirce(validatedJson)

    FromJsonTypingResultBasedDecoder
      .decodeValue(typingResult, circeJson.hcursor)
      .fold(e => throw CustomNodeValidationException(e.getMessage(), None), identity)
  }

}

object NoSchemaJsonPayloadDeserializer extends UniversalSchemaPayloadDeserializer {

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    val string = new String(bytes, StandardCharsets.UTF_8)

    val inputJson = new JSONTokener(string).nextValue()
    val circeJson = JsonSchemaUtils.jsonToCirce(inputJson)

    FromJsonSimpleDecoder.jsonToAny(circeJson)
  }

}
