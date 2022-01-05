package pl.touk.nussknacker.restmodel

import io.circe.{Encoder, Json}

import java.net.URI

object NuLink {

  def encoderForBasePath(baseUrl: String): Encoder[NuLink] = Encoder.instance { case NuLink(url) =>
    val stringForm = if (!url.isAbsolute) {
      new URI(baseUrl + url).normalize().toString
    } else url.normalize().toString
    Json.fromString(stringForm)
  }

  def apply(uri: String): NuLink = NuLink(URI.create(uri))

}

case class NuLink(url: URI)

