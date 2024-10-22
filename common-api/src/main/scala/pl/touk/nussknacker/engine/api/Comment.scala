package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder}

final case class Comment private (content: String) extends AnyVal {
  override def toString: String = content
}

object Comment {

  def from(content: String): Option[Comment] = {
    if (content.isEmpty) None else Some(Comment(content))
  }

  implicit val encoder: Encoder[Comment] = Encoder.encodeString.contramap(_.content)
  implicit val decoder: Decoder[Comment] = Decoder.decodeString.map(Comment.apply)
}
