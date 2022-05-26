package pl.touk.nussknacker.ui.listener

import io.circe.{Decoder, Encoder}

class Comment(comment: String) {
  def value: String = comment
}

object Comment {
  implicit val encoder: Encoder[Comment] = Encoder.encodeString.contramap(_.value)
}

