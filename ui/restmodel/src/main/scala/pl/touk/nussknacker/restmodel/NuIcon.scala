package pl.touk.nussknacker.restmodel

import io.circe.{Decoder, Encoder}

import java.net.URI

//TODO: handle icon encoding with absolute URL generation
object NuIcon {

  implicit val encoder: Encoder[NuIcon] = Encoder.encodeString.contramap(_.uri.normalize().toString)

  implicit val decoder: Decoder[NuIcon] = Decoder.decodeString.map(s => NuIcon(URI.create(s)))

  def apply(uri: String): NuIcon = NuIcon(URI.create(uri))

}

case class NuIcon(uri: URI)
