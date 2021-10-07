package pl.touk.nussknacker

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec

import java.net.{URI, URL}

package object openapi {
  type PropertyName = String
  type SwaggerRef = String

  implicit val urlEncoder: Encoder[URL] = Encoder.encodeString.contramap(_.toExternalForm)
  implicit val urlDecoder: Decoder[URL] = Decoder.decodeString.map(new URL(_))

  @JsonCodec sealed trait SwaggerParameter {

    def name: String

    def `type`: SwaggerTyped

  }

  @JsonCodec sealed trait PathPart

  final case class UriParameter(name: String, `type`: SwaggerTyped) extends SwaggerParameter

  final case class QueryParameter(name: String, `type`: SwaggerTyped) extends SwaggerParameter

  final case class HeaderParameter(name: String, `type`: SwaggerTyped) extends SwaggerParameter

  final case class SingleBodyParameter(`type`: SwaggerTyped) extends SwaggerParameter {
    val name = "body"
  }

  case class PlainPart(value: String) extends PathPart

  case class PathParameterPart(parameterName: String) extends PathPart

  //TODO: content type?
  @JsonCodec final case class SwaggerService(name: String,
                                             categories: List[String],
                                             documentation: Option[String],
                                             pathParts: List[PathPart],
                                             parameters: List[SwaggerParameter],
                                             responseSwaggerType: Option[SwaggerTyped],
                                             method: String,
                                             servers: List[URL],
                                             securities: List[SwaggerSecurity])

}
