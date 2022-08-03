package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer, AbstractKafkaSchemaSerDe}
import org.apache.avro.Schema
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schema.{DatumReaderWriterMixin, RecordDeserializer}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer

import java.io.IOException
import java.nio.ByteBuffer

/**
 * This class basically do the same as AbstractKafkaAvroDeserializer but use our createDatumReader implementation with time conversions
 */
abstract class AbstractConfluentKafkaAvroDeserializer extends AbstractKafkaAvroDeserializer {

  protected def schemaIdSerializationEnabled: Boolean

  protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get()
  private lazy val confluentAvroPayloadDeserializer = new ConfluentAvroPayloadDeserializer(useSchemaReflection, useSpecificAvroReader, schemaIdSerializationEnabled, decoderFactory)

  protected def deserialize(topic: String, isKey: java.lang.Boolean, payload: Array[Byte], readerSchema: Option[RuntimeSchemaData[AvroSchema]]): AnyRef = {
    val buffer = ConfluentUtils.parsePayloadToByteBuffer(payload).valueOr(ex => throw ex)
    read(buffer, readerSchema)
  }

  protected def read(buffer: ByteBuffer, expectedSchemaData: Option[RuntimeSchemaData[AvroSchema]]): AnyRef = {
    var schemaId = -1

    try {
      schemaId = buffer.getInt
      val parsedSchema = schemaRegistry.getSchemaById(schemaId)
      val writerSchemaData = RuntimeSchemaData(ConfluentUtils.extractSchema(parsedSchema), Some(schemaId))
      val bufferDataStart = 1 + AbstractKafkaSchemaSerDe.idSize
      confluentAvroPayloadDeserializer.deserialize(expectedSchemaData.map(_.toParsedSchemaData), writerSchemaData.toParsedSchemaData, buffer, bufferDataStart).asInstanceOf[AnyRef]
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }
}

object ConfluentAvroPayloadDeserializer {
  val default = new ConfluentAvroPayloadDeserializer(false, false, false, DecoderFactory.get())
}


class ConfluentAvroPayloadDeserializer(
                                        useSchemaReflection: Boolean,
                                        useSpecificAvroReader: Boolean,
                                        override val schemaIdSerializationEnabled: Boolean,
                                        override val decoderFactory: DecoderFactory
                                      ) extends DatumReaderWriterMixin with RecordDeserializer with UniversalSchemaPayloadDeserializer {

  override def deserialize(expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]], writerSchemaData: RuntimeSchemaData[ParsedSchema], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val avroExpectedSchemaData = expectedSchemaData.asInstanceOf[Option[RuntimeSchemaData[AvroSchema]]]
    val avroWriterSchemaData = writerSchemaData.asInstanceOf[RuntimeSchemaData[AvroSchema]]
    val readerSchemaData = avroExpectedSchemaData.getOrElse(avroWriterSchemaData)
    val reader = createDatumReader(avroWriterSchemaData.schema.rawSchema(), readerSchemaData.schema.rawSchema(), useSchemaReflection, useSpecificAvroReader)
    deserializeRecord(readerSchemaData, reader, buffer, bufferDataStart)
  }
}

object ConfluentJsonPayloadDeserializer extends UniversalSchemaPayloadDeserializer {

  private val converter = new JsonPayloadToAvroConverter(None)

  override def deserialize(expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]], writerSchemaData: RuntimeSchemaData[ParsedSchema], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val length = buffer.limit() - bufferDataStart
    val bytes = new Array[Byte](length)
    buffer.get(bytes, 0, length)
    val schema = writerSchemaData.schema.rawSchema().asInstanceOf[Schema]
    converter.convert(buffer.array(), schema)
  }
}

object ConfluentJsonSchemaPayloadDeserializer extends UniversalSchemaPayloadDeserializer {

  override def deserialize(expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]], writerSchemaData: RuntimeSchemaData[ParsedSchema], buffer: ByteBuffer, bufferDataStart: Int): Any = {
    val readerSchemaData = expectedSchemaData.getOrElse(writerSchemaData).asInstanceOf[RuntimeSchemaData[JsonSchema]]
    val length = buffer.limit() - bufferDataStart
    val bytes = new Array[Byte](length)
    buffer.get(bytes, 0, length)
    new CirceJsonDeserializer(readerSchemaData.schema.rawSchema()).deserialize(bytes).valueOr(e => throw new RuntimeException("Deserialization error", e)) //todo: maybe better exception
  }
}

trait UniversalSchemaPayloadDeserializer {
  def deserialize(expectedSchemaData: Option[RuntimeSchemaData[ParsedSchema]], writerSchemaData: RuntimeSchemaData[ParsedSchema], buffer: ByteBuffer, bufferDataStart: Int): Any
}