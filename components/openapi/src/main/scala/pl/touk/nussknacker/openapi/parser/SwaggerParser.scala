package pl.touk.nussknacker.openapi.parser

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.converter.SwaggerConverter
import io.swagger.v3.parser.core.models.ParseOptions
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}

import java.util.Collections

object SwaggerParser extends LazyLogging {

  def parse(rawSwagger: String, openAPIsConfig: OpenAPIServicesConfig): List[Validated[ServiceParseError, SwaggerService]] = {
    val openapi = parseToSwagger(rawSwagger)
    ParseToSwaggerServices(openapi, openAPIsConfig)
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

