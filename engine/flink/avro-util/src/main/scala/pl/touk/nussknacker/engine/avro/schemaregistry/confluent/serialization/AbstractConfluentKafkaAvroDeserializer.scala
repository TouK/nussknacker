package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.io.IOException
import java.nio.ByteBuffer

import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer, AbstractKafkaSchemaSerDe}
import org.apache.avro.Schema.Type
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

/**
 * This class basically do the same as AbstractKafkaAvroDeserializer but use our createDatumReader implementation with time conversions
 */
abstract class AbstractConfluentKafkaAvroDeserializer extends AbstractKafkaAvroDeserializer with DatumReaderWriterMixin {

  protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get()

  protected def deserialize(topic: String, isKey: java.lang.Boolean, payload: Array[Byte], readerSchema: RuntimeSchemaData): AnyRef = {
    val buffer = ConfluentUtils.parsePayloadToByteBuffer(payload).valueOr(ex => throw ex)
    read(buffer, readerSchema)
  }

  protected def read(buffer: ByteBuffer, expectedSchemaData: RuntimeSchemaData): AnyRef = {
    var schemaId = -1

    try {
      schemaId = buffer.getInt
      val parsedSchema = schemaRegistry.getSchemaById(schemaId)
      val writerSchemaData = RuntimeSchemaData(ConfluentUtils.extractSchema(parsedSchema), Some(schemaId))
      val readerSchemaData = if (expectedSchemaData == null) writerSchemaData else expectedSchemaData
      // HERE we create our DatumReader
      val reader = createDatumReader(writerSchemaData.schema, readerSchemaData.schema, useSchemaReflection, useSpecificAvroReader)
      val length = buffer.limit() - 1 - AbstractKafkaSchemaSerDe.idSize
      if (writerSchemaData.schema.getType == Type.BYTES) {
        val bytes = new Array[Byte](length)
        buffer.get(bytes, 0, length)
        bytes
      } else {
        val start = buffer.position() + buffer.arrayOffset
        val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
        val result = reader.read(null, binaryDecoder)
        if (writerSchemaData.schema.getType == Type.STRING) result.toString else result
      }
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }

}
