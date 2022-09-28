package pl.touk.nussknacker.engine.requestresponse

import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

case class OpenApiDefinitionConfig(servers: List[OApiServer] = List.empty)

case class RequestResponseConfig(definitionMetadata: OpenApiDefinitionConfig = OpenApiDefinitionConfig())
