package pl.touk.nussknacker.ui.process.repository

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.listener.InternalComment

class SystemComment(override val comment: String) extends InternalComment(comment)

case class UpdateProcessComment(override val comment: String) extends SystemComment(comment) {
  override def value: String = comment
}

object UpdateProcessComment {
  implicit val encoder: Encoder[UpdateProcessComment] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[UpdateProcessComment] = Decoder.decodeString.map(UpdateProcessComment(_))
}

case class UserComment(override val comment: String) extends InternalComment(comment)
