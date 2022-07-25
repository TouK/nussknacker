package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.serializers.{AbstractKafkaAvroDeserializer, AbstractKafkaSchemaSerDe}
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schema.{DatumReaderWriterMixin, RecordDeserializer}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
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
      confluentAvroPayloadDeserializer.deserialize(expectedSchemaData, writerSchemaData, buffer, bufferDataStart)
    } catch {
      case exc: RestClientException =>
        throw new SerializationException(s"Error retrieving Avro schema for id : $schemaId", exc)
      case exc@(_: RuntimeException | _: IOException) =>
        // avro deserialization may throw IOException, AvroRuntimeException, NullPointerException, etc
        throw new SerializationException(s"Error deserializing Avro message for id: $schemaId", exc)
    }
  }


}

class ConfluentAvroPayloadDeserializer(
                                        useSchemaReflection: Boolean,
                                        useSpecificAvroReader: Boolean,
                                        override val schemaIdSerializationEnabled: Boolean,
                                        override val decoderFactory: DecoderFactory
                                      ) extends DatumReaderWriterMixin with RecordDeserializer {

  def deserialize(expectedSchemaData: Option[RuntimeSchemaData[AvroSchema]], writerSchemaData: RuntimeSchemaData[AvroSchema], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val readerSchemaData = expectedSchemaData.getOrElse(writerSchemaData)
    val reader = createDatumReader(writerSchemaData.schema.rawSchema(), readerSchemaData.schema.rawSchema(), useSchemaReflection, useSpecificAvroReader)
    deserializeRecord(readerSchemaData, reader, buffer, bufferDataStart)
  }
}

class ConfluentJsonSchemaPayloadDeserializer {

  def deserialize(expectedSchemaData: Option[RuntimeSchemaData[JsonSchema]], writerSchemaData: RuntimeSchemaData[JsonSchema], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val readerSchemaData = expectedSchemaData.getOrElse(writerSchemaData)
    new CirceJsonDeserializer(readerSchemaData.schema.rawSchema()).deserialize(buffer.array())
  }
}