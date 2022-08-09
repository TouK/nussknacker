package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal

import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils.readIdAndGetBuffer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.{KeySchemaIdHeaderName, ValueSchemaIdHeaderName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde._

import java.nio.ByteBuffer
import scala.util.Try

case class SchemaIdWithPositionedBuffer(value: Int, buffer: ByteBuffer) {
  def bufferStartPosition: Int = buffer.position()
}

trait UniversalSchemaIdFromMessageExtractor {

  val schemaRegistryClient: ConfluentSchemaRegistryClient

  // SchemaId can be obtain in several ways. Precedent:
  // * from kafka header
  // * from payload serialized in 'Confluent way' ([magicbyte][schemaid][payload])
  // * from source editor version param - this is just an assumption we make (when processing no-schemed-data, everything can happen)
  def getSchemaId(headers: Headers, data: Array[Byte], isKey: Boolean, fallback: Option[Int]): SchemaIdWithPositionedBuffer = {
    val headerName = if (isKey) KeySchemaIdHeaderName else ValueSchemaIdHeaderName

    headers.getSchemaId(headerName) match {
      case Some(idFromHeader) => // Even if schemaId is passed through header, it still can be serialized in 'Confluent way', here we're figuring it out
        val buffer = readIdAndGetBuffer(data).toOption.map(_._2).getOrElse(ByteBuffer.wrap(data))
        SchemaIdWithPositionedBuffer(idFromHeader, buffer)
      case None =>
        val idAndBuffer = ConfluentUtils.readIdAndGetBuffer(data).toOption
          .getOrElse(
            fallback.map((_, ByteBuffer.wrap(data)))
              .getOrElse(throw new MessageWithoutSchemaIdException()))
        SchemaIdWithPositionedBuffer(idAndBuffer._1, buffer = idAndBuffer._2)
    }
  }

  def getSchemaIdWhenPresent(headers: Headers, data: Array[Byte], isKey: Boolean): Option[SchemaIdWithPositionedBuffer] =
    Try(getSchemaId(headers, data, isKey, fallback = None)).map(Some(_))
      .recover({
        case _: MessageWithoutSchemaIdException => None
      }).get
}

class MessageWithoutSchemaIdException extends IllegalArgumentException("Missing schemaId in kafka headers, in payload, and no fallback provided")