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

case class ApiKeyInHeader(name: String, key: String) extends SecurityInHeader {
  def value: String = key
}

case class ApiKeyInQuery(name: String, key: String) extends SwaggerSecurity {
  def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request.method(request.method, request.uri.param(name, key))
}

case class ApiKeyInCookie(name: String, key: String) extends SwaggerSecurity {
  def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request.cookie(name, key)
}