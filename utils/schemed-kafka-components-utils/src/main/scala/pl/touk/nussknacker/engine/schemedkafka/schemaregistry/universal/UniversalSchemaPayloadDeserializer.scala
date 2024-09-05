package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.io.{DatumReader, DecoderFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroRecordDeserializer, DatumReaderWriterMixin}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

trait UniversalSchemaPayloadDeserializer {

  def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): Any

}

object AvroPayloadDeserializer {
  def apply(config: KafkaConfig) =
    new AvroPayloadDeserializer(false, false, GenericRecordSchemaIdSerializationSupport(config), DecoderFactory.get())
}

// This implementation is based on Confluent's one but currently deosn't use any Confluent specific things
class AvroPayloadDeserializer(
    useSchemaReflection: Boolean,
    useSpecificAvroReader: Boolean,
    genericRecordSchemaIdSerializationSupport: GenericRecordSchemaIdSerializationSupport,
    decoderFactory: DecoderFactory
) extends DatumReaderWriterMixin
    with UniversalSchemaPayloadDeserializer {

  @transient private lazy val readerCache = new ConcurrentHashMap[ReaderKey, DatumReader[AnyRef]]()

  private val recordDeserializer = new AvroRecordDeserializer(decoderFactory)

  override def deserialize(
      expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]],
      writerSchemaData: RuntimeSchemaData[ParsedSchema],
      buffer: ByteBuffer
  ): AnyRef = {
    val avroExpectedSchemaData = expectedSchemaData.asInstanceOf[Option[RuntimeSchemaData[AvroSchema]]]
    val avroWriterSchemaData   = writerSchemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
    val readerSchemaData       = avroExpectedSchemaData.getOrElse(avroWriterSchemaData)

    // Creating a datum reader is very expensive operation, therefore we cache it
    val reader = readerCache.computeIfAbsent(
      ReaderKey(readerSchemaData.schema, avroWriterSchemaData.schema),
      (key: ReaderKey) => {
        createDatumReader(
          key.writerSchema.rawSchema(),
          key.readerSchema.rawSchema(),
          useSchemaReflection,
          useSpecificAvroReader
        )
      }
    )

    val result = recordDeserializer.deserializeRecord(readerSchemaData.schema.rawSchema(), reader, buffer)
    genericRecordSchemaIdSerializationSupport.wrapWithRecordWithSchemaIdIfNeeded(result, readerSchemaData)
  }

  private case class ReaderKey(readerSchema: AvroSchema, writerSchema: AvroSchema)
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
