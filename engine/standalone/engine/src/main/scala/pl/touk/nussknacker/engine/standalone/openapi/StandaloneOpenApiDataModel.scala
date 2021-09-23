package pl.touk.nussknacker.engine.standalone.openapi

import io.circe.Json
import io.circe.derivation.annotations.JsonCodec

@JsonCodec case class OApiDocumentation(openapi: String, info: OApiInfo, servers: List[OApiServer], paths: Json)
@JsonCodec case class OApiServer(url: String, description: String)
@JsonCodec case class OApiInfo(title: String, description: String, contact: Option[OApiContact])
@JsonCodec case class OApiContact(name: String, email: String)
