package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.io.{DatumReader, DecoderFactory}

import java.nio.ByteBuffer

class AvroRecordDeserializer(decoderFactory: DecoderFactory) {

  def deserializeRecord(readerSchema: Schema, reader: DatumReader[AnyRef], buffer: ByteBuffer): AnyRef = {
    if (readerSchema.getType == Type.BYTES) {
      val bytes = new Array[Byte](buffer.remaining())
      buffer.get(bytes)
      bytes
    } else {
      val start = buffer.position() + buffer.arrayOffset
      val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, buffer.remaining(), null)
      val result = reader.read(null, binaryDecoder)
      result match {
        case _ if readerSchema.getType == Type.STRING => result.toString
        case _ => result
      }
    }
  }

}
