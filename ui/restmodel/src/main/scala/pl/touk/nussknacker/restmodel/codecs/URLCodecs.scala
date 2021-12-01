package pl.touk.nussknacker.restmodel.codecs

import io.circe.{Decoder, Encoder}

import java.net.URL

object URLCodecs {
  implicit val urlEncoder: Encoder[URL] = Encoder.encodeString.contramap(_.toString)
  implicit val urlDecoder: Decoder[URL] = Decoder.decodeString.map(new URL(_))
}
