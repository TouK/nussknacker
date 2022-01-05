package pl.touk.nussknacker.restmodel

import io.circe.Encoder

import java.net.URI

object NuIcon {

  implicit def encoder(implicit encoder: Encoder[NuLink]): Encoder[NuIcon] = encoder.contramap[NuIcon] { case NuIcon(uri) =>
    NuLink(
      if (uri.isAbsolute) uri else URI.create("/static/" + uri.toString)
    )
  }

  def apply(uri: String): NuIcon = NuIcon(URI.create(uri))

}

case class NuIcon(uri: URI)
