package pl.touk.nussknacker.openapi.parser

import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import io.swagger.v3.oas.models.servers.Server
import pl.touk.nussknacker.openapi.{OpenAPISecurityConfig, SwaggerService}

import scala.collection.JavaConverters._

private[parser] object ParseToSwaggerServices {

  def apply(paths: Map[String, PathItem],
            swaggerRefSchemas: SwaggerRefSchemas,
            servers: List[Server],
            globalSecurityRequirements: List[SecurityRequirement],
            securitySchemes: Option[Map[String, SecurityScheme]],
            securities: Map[String, OpenAPISecurityConfig]): List[SwaggerService] =
    for {
      (uriWithParameters, endpoint) <- paths.toList
      parseToService = new ParseToSwaggerService(uriWithParameters, swaggerRefSchemas, servers, globalSecurityRequirements,
        securitySchemes, securities)
      (method, endpointDefinition) <- endpoint.readOperationsMap.asScala.toList
      service <- parseToService(method, endpointDefinition)
    } yield service
}
