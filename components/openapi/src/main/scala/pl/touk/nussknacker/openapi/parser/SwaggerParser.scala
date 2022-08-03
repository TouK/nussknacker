package pl.touk.nussknacker.openapi.parser

import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.converter.SwaggerConverter
import io.swagger.v3.parser.core.models.ParseOptions
import pl.touk.nussknacker.engine.json.swagger.parser.ParseSwaggerRefSchemas
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}

import java.util.Collections
import scala.collection.JavaConverters._

object SwaggerParser extends LazyLogging {

  def parse(rawSwagger: String, openAPIsConfig: OpenAPIServicesConfig): List[SwaggerService] = {
    val openapi = parseToSwagger(rawSwagger)
    val securitySchemas = Option(openapi.getComponents.getSecuritySchemes).map(_.asScala.toMap)
    val allServices = ParseToSwaggerServices(
      paths = openapi.getPaths.asScala.toMap,
      swaggerRefSchemas = ParseSwaggerRefSchemas(openapi),
      openapi.getServers.asScala.toList,
      Option(openapi.getSecurity).map(_.asScala.toList).getOrElse(Nil),
      securitySchemas,
      openAPIsConfig.security.getOrElse(Map.empty)
    )
    val (acceptedServices, droppedServices)
      = allServices.partition(service => openAPIsConfig.allowedMethods.contains(service.method) && service.name.matches(openAPIsConfig.namePattern.regex))
    if (droppedServices.nonEmpty) {
      logger.info(s"Following services were filtered out by rules (name must match: ${openAPIsConfig.namePattern}, allowed HTTP methods: ${openAPIsConfig.allowedMethods.mkString(", ")}): " +
        s"${droppedServices.map(_.name).mkString(", ")}")
    }
    acceptedServices
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

