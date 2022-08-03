package pl.touk.nussknacker.engine.schemedkafka.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericData
import org.apache.avro.io.{DatumReader, DecoderFactory}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

import java.nio.ByteBuffer

trait RecordDeserializer {

  protected def decoderFactory: DecoderFactory

  protected def deserializeRecord(readerSchemaData: RuntimeSchemaData[AvroSchema], reader: DatumReader[AnyRef], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val length = buffer.limit() - bufferDataStart
    if (readerSchemaData.schema.rawSchema().getType == Type.BYTES) {
      val bytes = new Array[Byte](length)
      buffer.get(bytes, 0, length)
      bytes
    } else {
      val start = buffer.position() + buffer.arrayOffset
      val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
      val result = reader.read(null, binaryDecoder)
      result match {
        case _ if readerSchemaData.schema.rawSchema().getType == Type.STRING => result.toString
        case genericRecord: GenericData.Record if schemaIdSerializationEnabled =>
          val readerSchemaId = readerSchemaData.schemaIdOpt.getOrElse(throw new IllegalStateException("SchemaId serialization enabled but schemaId missed from reader schema data"))
          new GenericRecordWithSchemaId(genericRecord, readerSchemaId, false)
        case _ => result
      }
    }
  }

  protected def schemaIdSerializationEnabled: Boolean

}
