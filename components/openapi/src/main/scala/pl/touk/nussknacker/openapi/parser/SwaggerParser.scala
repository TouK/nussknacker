package pl.touk.nussknacker.openapi.parser

import java.util.Collections

import com.typesafe.config.Config
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.converter.SwaggerConverter
import io.swagger.v3.parser.core.models.ParseOptions
import pl.touk.nussknacker.openapi.{OpenAPISecurityConfig, OpenAPIServicesConfig, SwaggerService}

import scala.collection.JavaConverters._

object SwaggerParser {

  def parse(rawSwagger: String, openAPIsConfig: OpenAPIServicesConfig): List[SwaggerService] = {
    val openapi = parseToSwagger(rawSwagger)
    val securitySchemas = Option(openapi.getComponents.getSecuritySchemes).map(_.asScala.toMap)
    ParseToSwaggerServices(
      paths = openapi.getPaths.asScala.toMap,
      swaggerRefSchemas = ParseSwaggerRefSchemas(openapi),
      openapi.getServers.asScala.toList,
      Option(openapi.getSecurity).map(_.asScala.toList).getOrElse(Nil),
      securitySchemas,
      openAPIsConfig.securities.getOrElse(Map.empty)
    ).filter(service => openAPIsConfig.allowedMethods.contains(service.method))
     .filter(_.name.matches(openAPIsConfig.namePattern.regex))

  }

  private[parser] def parseToSwagger(rawSwagger: String): OpenAPI = {
    val swagger30 = new OpenAPIV3Parser().readContents(rawSwagger)
    Option(swagger30.getOpenAPI)
      .getOrElse {
        val swagger20 = new SwaggerConverter().readContents(rawSwagger, Collections.emptyList(), new ParseOptions)
        if (swagger20.getOpenAPI != null) {
          swagger20.getOpenAPI
        } else throw new IllegalArgumentException(s"Failed to parse with swagger 3.0: ${swagger30.getMessages} and 2.0 ${swagger20.getMessages}")
      }
  }
}

