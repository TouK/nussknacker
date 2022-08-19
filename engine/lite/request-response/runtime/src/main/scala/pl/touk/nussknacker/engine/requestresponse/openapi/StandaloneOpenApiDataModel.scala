package pl.touk.nussknacker.engine.requestresponse.openapi

import io.circe.{Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveEncoder

object OApiDocumentation {
  def dropNulls[A](encoder: Encoder[A]): Encoder[A] = encoder.mapJson(_.dropNullValues)
  implicit val oApiDocumentationEncoder: Encoder[OApiDocumentation] = dropNulls(deriveEncoder)
  implicit val oApiInfoEncoder: Encoder[OApiInfo] = dropNulls(deriveEncoder)
}
case class OApiDocumentation(openapi: String, info: OApiInfo, servers: Option[List[OApiServer]], paths: Json)
@JsonCodec(encodeOnly = true)
case class OApiServer(url: String, description: String)
case class OApiInfo(title: String, description: Option[String] = None, contact: Option[OApiContact] = None, version: String)
@JsonCodec(encodeOnly = true)
case class OApiContact(name: String, url: String, email: String)
