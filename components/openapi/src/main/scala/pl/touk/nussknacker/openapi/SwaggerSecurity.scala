package pl.touk.nussknacker.openapi

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.openapi.extractor.ServiceRequest.SwaggerRequestType

//TODO: enable adding custom Security settings
@JsonCodec sealed trait SwaggerSecurity {
  def addSecurity(request: SwaggerRequestType): SwaggerRequestType
}

sealed trait SecurityInHeader extends SwaggerSecurity {
  def name: String

  def value: String

  def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request.header(name, value)
}

final case class ApiKeyInHeader(name: String, key: String) extends SecurityInHeader {
  def value: String = key
}

final case class ApiKeyInQuery(name: String, key: String) extends SwaggerSecurity {
  def addSecurity(request: SwaggerRequestType): SwaggerRequestType =
    request.method(request.method, request.uri.addParam(name, key))
}

final case class ApiKeyInCookie(name: String, key: String) extends SwaggerSecurity {
  def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request.cookie(name, key)
}

final case class BasicAuth(name: String, username: String, password: String) extends SwaggerSecurity {
  override def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request.auth.basic(username, password)
}

final case class OAuth2ClientCredentials(
    name: String,
    tokenUrl: String,
    clientId: String,
    clientSecret: String,
    scope: Option[String]
) extends SwaggerSecurity {

  // OAuth2 client credentials are applied on backend send stage where token provider can be used.
  override def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request

}
