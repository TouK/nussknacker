package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.io.{DatumReader, DecoderFactory}

import java.nio.ByteBuffer

class AvroRecordDeserializer(decoderFactory: DecoderFactory) {

  def deserializeRecord(readerSchema: Schema, reader: DatumReader[AnyRef], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val length = buffer.limit() - bufferDataStart
    if (readerSchema.getType == Type.BYTES) {
      val bytes = new Array[Byte](length)
      buffer.get(bytes, 0, length)
      bytes
    } else {
      val start = buffer.position() + buffer.arrayOffset
      val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
      val result = reader.read(null, binaryDecoder)
      result match {
        case _ if readerSchema.getType == Type.STRING => result.toString
        case _ => result
      }
    }
  }

}
