package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import java.nio.ByteBuffer

import cats.data.Validated

object ConfluentUtils {

  def parsePayloadToByteBuffer(payload: Array[Byte]): Validated[IllegalArgumentException, ByteBuffer] = {
    val buffer = ByteBuffer.wrap(payload)
    if (buffer.get != 0)
      Validated.invalid(new IllegalArgumentException("Unknown magic byte!"))
    else
      Validated.valid(buffer)
  }
}
