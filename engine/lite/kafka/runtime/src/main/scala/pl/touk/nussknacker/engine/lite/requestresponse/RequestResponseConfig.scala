package pl.touk.nussknacker.engine.lite.requestresponse

import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

case class OpenApiDefinitionConfig(server: Option[OApiServer] = None)

case class RequestResponseConfig(definitionMetadata: OpenApiDefinitionConfig = OpenApiDefinitionConfig())