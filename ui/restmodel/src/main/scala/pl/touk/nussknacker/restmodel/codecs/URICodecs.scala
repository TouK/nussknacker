package pl.touk.nussknacker.restmodel.codecs

import io.circe.{Decoder, Encoder}

import java.net.URI

object URICodecs {

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)
}
