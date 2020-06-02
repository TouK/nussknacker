package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import java.nio.ByteBuffer

import cats.data.Validated
import org.apache.kafka.common.errors.SerializationException

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

  def readId(bytes: Array[Byte]): Int =
    ConfluentUtils
      .parsePayloadToByteBuffer(bytes)
      .valueOr(exc => throw new SerializationException(exc.getMessage, exc))
      .getInt
}
