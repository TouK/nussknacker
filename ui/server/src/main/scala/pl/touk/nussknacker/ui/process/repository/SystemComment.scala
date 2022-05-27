package pl.touk.nussknacker.ui.process.repository

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.listener.Comment

sealed trait SystemComment extends Comment

case class UpdateProcessComment(comment: String) extends SystemComment {
  override def value: String = comment
}

object UpdateProcessComment {
  implicit val encoder: Encoder[UpdateProcessComment] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[UpdateProcessComment] = Decoder.decodeString.map(UpdateProcessComment(_))
}
