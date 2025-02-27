package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid

import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  GetSchemaIdArgs,
  SchemaId,
  SchemaIdFromMessageExtractor,
  SchemaIdWithPositionedBuffer
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.util.Try

import SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload._

object SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload {

  val ValueSchemaIdHeaderName = "value.schemaId"
  val KeySchemaIdHeaderName   = "key.schemaId"

}

/**
  * This class basically extract schema id from our specific headers: key/value.schemaId.
  * Because we always produce those headers, we can have situation when schema id is in headers but also payload
  * is in Confluent format (magic byte + schema id + bytes). Because of that we need to shift this payload
  * so next step (payload deserializer) will have clear situation - buffer pointer pointing to bytes with message
  */
class SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload(
    intSchemaId: Boolean,
    potentiallyShiftConfluentPayload: Boolean
) extends SchemaIdFromMessageExtractor {

  implicit class RichHeaders(h: Headers) {

    def getSchemaId(headerName: String): Option[SchemaId] = Option(h.lastHeader(headerName))
      .map(header => new String(header.value(), StandardCharsets.UTF_8))
      .map { stringValue =>
        if (intSchemaId) {
          Try(stringValue.toInt)
            .fold(e => throw new InvalidSchemaIdHeader(headerName, stringValue, e), SchemaId.fromInt)
        } else {
          SchemaId.fromString(stringValue)
        }
      }

  }

  override def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer] = {
    val headerName = if (args.isKey) KeySchemaIdHeaderName else ValueSchemaIdHeaderName
    args.headers.getSchemaId(headerName).map { idFromHeader =>
      val buffer = if (potentiallyShiftConfluentPayload) {
        ConfluentUtils.readIdAndGetBuffer(args.data).toOption.map(_._2).getOrElse(ByteBuffer.wrap(args.data))
      } else {
        ByteBuffer.wrap(args.data)
      }
      SchemaIdWithPositionedBuffer(idFromHeader, buffer)
    }
  }

}

class InvalidSchemaIdHeader(headerName: String, value: String, cause: Throwable)
    extends IllegalArgumentException(s"Got header $headerName, but the value '$value' is invalid.", cause)
