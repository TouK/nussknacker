package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid

import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GetSchemaIdArgs, SchemaId, SchemaIdFromMessageExtractor, SchemaIdWithPositionedBuffer}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

import java.nio.ByteBuffer
import scala.util.Try

object SchemaIdFromNuHeadersAndPotentiallyConfluentPayload extends SchemaIdFromMessageExtractor with Serializable {

  val ValueSchemaIdHeaderName = "value.schemaId"
  val KeySchemaIdHeaderName = "key.schemaId"

  implicit class RichHeaders(h: Headers) {
    def getSchemaId(headerName: String): Option[SchemaId] = Option(h.lastHeader(headerName))
      .map(h => new String(h.value()))
      .map { v =>
        Try(v.toInt).fold(
          e => throw new InvalidSchemaIdHeader(headerName, v, e),
          // TODO: handle string schema ids
          v => SchemaId.fromInt(v))
      }
  }

  override def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer] = {
    val headerName = if (args.isKey) KeySchemaIdHeaderName else ValueSchemaIdHeaderName
    args.headers.getSchemaId(headerName).map { idFromHeader =>
      val buffer = ConfluentUtils.readIdAndGetBuffer(args.data).toOption.map(_._2).getOrElse(ByteBuffer.wrap(args.data))
      SchemaIdWithPositionedBuffer(idFromHeader, buffer)
    }
  }

}

class InvalidSchemaIdHeader(headerName: String, value: String, cause: Throwable) extends IllegalArgumentException(s"Got header $headerName, but the value '$value' is invalid.", cause)
