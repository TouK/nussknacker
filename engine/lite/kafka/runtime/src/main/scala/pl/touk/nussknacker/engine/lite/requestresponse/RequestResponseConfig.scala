package pl.touk.nussknacker.engine.lite.requestresponse

import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

case class OpenApiDefinitionConfig(server: Option[OApiServer] = None)

case class RequestResponseConfig(port: Int, interface: String = "0.0.0.0", definitionMetadata: OpenApiDefinitionConfig = OpenApiDefinitionConfig())