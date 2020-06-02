package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import java.nio.ByteBuffer

import cats.data.Validated

object ConfluentUtils {
  //Copied from AbstractKafkaAvroSerDe.MAGIC_BYTE
  final val MagicByte = 0

  def parsePayloadToByteBuffer(payload: Array[Byte]): Validated[IllegalArgumentException, ByteBuffer] = {
    val buffer = ByteBuffer.wrap(payload)
    if (buffer.get != MagicByte)
      Validated.invalid(new IllegalArgumentException("Unknown magic byte!"))
    else
      Validated.valid(buffer)
  }
}
