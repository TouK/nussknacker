package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import java.io.IOException
import java.nio.ByteBuffer

import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer, AbstractKafkaSchemaSerDe}
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schema.{DatumReaderWriterMixin, RecordDeserializer}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

/**
 * This class basically do the same as AbstractKafkaAvroDeserializer but use our createDatumReader implementation with time conversions
 */
abstract class AbstractConfluentKafkaAvroDeserializer extends AbstractKafkaAvroDeserializer with DatumReaderWriterMixin with RecordDeserializer {

  override protected lazy val decoderFactory: DecoderFactory = DecoderFactory.get()

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
      val bufferDataStart = 1 + AbstractKafkaSchemaSerDe.idSize
      deserializeRecord(readerSchemaData, reader, buffer, bufferDataStart)
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }

}
