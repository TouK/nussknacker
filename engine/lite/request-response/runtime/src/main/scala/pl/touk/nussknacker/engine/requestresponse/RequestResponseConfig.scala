package pl.touk.nussknacker.engine.requestresponse

import pl.touk.nussknacker.engine.requestresponse.OpenApiDefinitionConfig.defaultOpenApiVersion
import pl.touk.nussknacker.engine.requestresponse.openapi.OApiServer

// Warning: openApiVersion config is undocumented feature - it changes only version in generated definition - not the way how it is generated
final case class OpenApiDefinitionConfig(
    servers: List[OApiServer] = List.empty,
    openApiVersion: String = defaultOpenApiVersion
)

object OpenApiDefinitionConfig {

  // By default we uses 3.0.0 version, because it is latest version supported by swagger-ui: https://github.com/swagger-api/swagger-ui/issues/5891
  val defaultOpenApiVersion = "3.0.0"

}

final case class BasicAuthConfig(user: String, password: String)

final case class RequestResponseSecurityConfig(basicAuth: Option[BasicAuthConfig] = None)

final case class RequestResponseConfig(
    definitionMetadata: OpenApiDefinitionConfig = OpenApiDefinitionConfig(),
    security: Option[RequestResponseSecurityConfig] = None
)
