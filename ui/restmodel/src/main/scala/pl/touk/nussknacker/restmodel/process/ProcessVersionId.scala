package pl.touk.nussknacker.restmodel.process

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}

object ProcessVersionId {
  implicit val ProcessVersionIdEncoder: Encoder[ProcessVersionId] = deriveUnwrappedEncoder
  implicit val ProcessVersionIdDecoder: Decoder[ProcessVersionId] = deriveUnwrappedDecoder
}

case class ProcessVersionId(value: Long) extends AnyVal
