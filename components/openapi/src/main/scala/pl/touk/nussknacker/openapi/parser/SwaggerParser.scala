package pl.touk.nussknacker.openapi.parser

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}

import java.util.Collections

object SwaggerParser extends LazyLogging {

  def parse(
      rawSwagger: String,
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {
    val openapi = parseToSwagger(rawSwagger)
    ParseToSwaggerServices(openapi, openAPIsConfig)
  }

  def loadFromResource(
      path: String,
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] =
    parse(ResourceLoader.load(path), openAPIsConfig)

  private[parser] def parseToSwagger(rawSwagger: String): OpenAPI = {
    val parserConfig = new ParseOptions()
    parserConfig.setResolve(true)

    val parserResult = new OpenAPIParser().readContents(rawSwagger, Collections.emptyList(), parserConfig)
    Option(parserResult.getOpenAPI).getOrElse(
      throw new IllegalArgumentException(s"Failed to parse OpenAPI specification: ${parserResult.getMessages}")
    )
  }

}
