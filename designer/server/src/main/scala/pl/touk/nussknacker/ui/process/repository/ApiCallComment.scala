package pl.touk.nussknacker.ui.process.repository

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import pl.touk.nussknacker.ui.listener.Comment
import sttp.tapir.Schema

// This type of comments is reserved for comments added by API call. API call might be done by a user or external application
final case class ApiCallComment(override val value: String) extends Comment

object ApiCallComment {

  implicit val encoder: Encoder[ApiCallComment] = deriveUnwrappedEncoder

  implicit val decoder: Decoder[ApiCallComment] = deriveUnwrappedDecoder

  implicit val schema: Schema[ApiCallComment] = Schema.string

}
