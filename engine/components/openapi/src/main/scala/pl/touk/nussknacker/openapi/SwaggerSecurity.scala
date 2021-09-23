package pl.touk.nussknacker.openapi

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.openapi.extractor.ServiceRequest.SwaggerRequestType

//TODO: enable adding custom Security settings
@JsonCodec sealed trait SwaggerSecurity {
  def addSecurity(request: SwaggerRequestType): SwaggerRequestType
}

@JsonCodec sealed trait SecurityInHeader extends SwaggerSecurity {
  def name: String

  def value: String

  def addSecurity(request: SwaggerRequestType): SwaggerRequestType = request.header(name, value)
}

@JsonCodec case class ApiKeyInHeader(name: String, key: String) extends SecurityInHeader {
  def value: String = key
}