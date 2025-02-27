package pl.touk.nussknacker

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped

import java.net.URL

package object openapi {

  implicit val urlEncoder: Encoder[URL] = Encoder.encodeString.contramap(_.toExternalForm)
  implicit val urlDecoder: Decoder[URL] = Decoder.decodeString.map(new URL(_))
}

package openapi {

  @JsonCodec sealed trait SwaggerParameter {

    def name: String

    def `type`: SwaggerTyped

  }

  @JsonCodec sealed trait PathPart

  final case class UriParameter(name: String, `type`: SwaggerTyped) extends SwaggerParameter

  final case class QueryParameter(name: String, `type`: SwaggerTyped) extends SwaggerParameter

  final case class HeaderParameter(name: String, `type`: SwaggerTyped) extends SwaggerParameter

  final case class SingleBodyParameter(`type`: SwaggerTyped) extends SwaggerParameter {
    override def name: String = SingleBodyParameter.name
  }

  object SingleBodyParameter {
    val name = "body"
  }

  final case class PlainPart(value: String) extends PathPart

  final case class PathParameterPart(parameterName: String) extends PathPart

  @JsonCodec final case class SwaggerService(
      name: ServiceName,
      categories: List[String],
      documentation: Option[String],
      pathParts: List[PathPart],
      parameters: List[SwaggerParameter],
      responseSwaggerType: Option[SwaggerTyped],
      method: String,
      servers: List[String],
      securities: List[SwaggerSecurity],
      requestContentType: Option[String]
  )

  final case class ServiceName(value: String)

  object ServiceName {
    implicit val encoder: Encoder[ServiceName] = Encoder.encodeString.contramap(_.value)
    implicit val decoder: Decoder[ServiceName] = Decoder.decodeString.map(ServiceName(_))
  }

}
