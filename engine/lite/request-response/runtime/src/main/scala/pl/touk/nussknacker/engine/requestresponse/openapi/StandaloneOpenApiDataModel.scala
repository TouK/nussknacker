package pl.touk.nussknacker.engine.requestresponse.openapi

import io.circe.Json

case class OApiDocumentation(openapi: String, info: OApiInfo, servers: List[OApiServer], paths: Json)
case class OApiServer(url: String, description: String)
case class OApiInfo(title: String, description: String, contact: Option[OApiContact])
case class OApiContact(name: String, email: String)
