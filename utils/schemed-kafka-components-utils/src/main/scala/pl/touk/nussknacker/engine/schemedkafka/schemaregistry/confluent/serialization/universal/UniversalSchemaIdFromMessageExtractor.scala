package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal

import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils.readIdAndGetBuffer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.{KeySchemaIdHeaderName, ValueSchemaIdHeaderName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde._

import java.nio.ByteBuffer

case class SchemaIdWithPositionedBuffer(value: Int, buffer: ByteBuffer) {
  def bufferStartPosition: Int = buffer.position()
}

trait UniversalSchemaIdFromMessageExtractor {

  val schemaRegistryClient: ConfluentSchemaRegistryClient

  // SchemaId can be obtain in several ways. Precedent:
  // * from kafka header
  // * from payload serialized in 'Confluent way' ([magicbyte][schemaid][payload])
  // * latest schema for topic
  def getSchemaId(topic: String, isKey: Boolean, headers: Headers, data: Array[Byte]): SchemaIdWithPositionedBuffer = {
    val headerName = if (isKey) KeySchemaIdHeaderName else ValueSchemaIdHeaderName

    headers.getSchemaId(headerName) match {
      case Some(idFromHeader) => // Even if schemaId is passed through header, it still can be serialized in 'Confluent way', here we're figuring it out
        val buffer = readIdAndGetBuffer(data).toOption.map(_._2).getOrElse(ByteBuffer.wrap(data))
        SchemaIdWithPositionedBuffer(idFromHeader, buffer)
      case None =>
        val idAndBuffer = ConfluentUtils.readIdAndGetBuffer(data).toOption
          .getOrElse(schemaRegistryClient
            .getLatestSchemaId(topic, isKey = isKey).map((_, ByteBuffer.wrap(data)))
            .valueOr(e => throw new RuntimeException("Missing schemaId in kafka header and in payload. Trying to fetch latest schema for this topic but it failed", e)))
        SchemaIdWithPositionedBuffer(idAndBuffer._1, buffer = idAndBuffer._2)
    }
  }
}
