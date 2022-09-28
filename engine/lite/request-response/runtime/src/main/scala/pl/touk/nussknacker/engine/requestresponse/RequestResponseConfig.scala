package pl.touk.nussknacker.engine.requestresponse

import pl.touk.nussknacker.engine.requestresponse.OpenApiDefinitionConfig.defaultOpenApiVersion
import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

case class OpenApiDefinitionConfig(servers: List[OApiServer] = List.empty, openApiVersion: String = defaultOpenApiVersion)

object OpenApiDefinitionConfig {

  // By default we uses 3.0.0 version, because it is latest version supported by swagger-ui: https://github.com/swagger-api/swagger-ui/issues/5891
  val defaultOpenApiVersion = "3.0.0"

}

case class RequestResponseConfig(definitionMetadata: OpenApiDefinitionConfig = OpenApiDefinitionConfig())
