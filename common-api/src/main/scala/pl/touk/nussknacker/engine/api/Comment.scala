package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder}

final case class Comment(content: String) extends AnyVal {
  override def toString: String = content
}

object Comment {
  implicit val encoder: Encoder[Comment] = Encoder.encodeString.contramap(_.content)
  implicit val decoder: Decoder[Comment] = Decoder.decodeString.map(Comment.apply)
}
