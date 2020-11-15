package pl.touk.nussknacker.engine.avro.schema

import java.nio.ByteBuffer

import org.apache.avro.Schema.Type
import org.apache.avro.io.{DatumReader, DecoderFactory}
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData

trait RecordDeserializer {

  protected def decoderFactory: DecoderFactory

  protected def deserializeRecord(readerSchemaData: RuntimeSchemaData, reader: DatumReader[AnyRef], buffer: ByteBuffer, bufferDataStart: Int): AnyRef = {
    val length = buffer.limit() - bufferDataStart
    if (readerSchemaData.schema.getType == Type.BYTES) {
      val bytes = new Array[Byte](length)
      buffer.get(bytes, 0, length)
      bytes
    } else {
      val start = buffer.position() + buffer.arrayOffset
      val binaryDecoder = decoderFactory.binaryDecoder(buffer.array, start, length, null)
      val result = reader.read(null, binaryDecoder)
      result match {
        case _ if readerSchemaData.schema.getType == Type.STRING => result.toString
        case _ => result
      }
    }
  }

}
