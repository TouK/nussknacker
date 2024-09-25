package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder}

final case class Comment(content: String)

object Comment {

  implicit val encoder: Encoder[Comment] = Encoder.encodeString.contramap(_.content)

  implicit val decoder: Decoder[Comment] = Decoder.decodeString.map(Comment.apply)

}
