package pl.touk.nussknacker.openapi.enrichers

import java.net.URL
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.api.{EagerService, Service}
import pl.touk.nussknacker.engine.api.definition.ServiceWithExplicitMethod
import pl.touk.nussknacker.engine.api.process.{SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.openapi.http.backend.HttpClientConfig
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.openapi.{OpenAPISecurityConfig, OpenAPIServicesConfig, OpenAPIsConfig, SwaggerService}

class SwaggerEnrichers(baseUrl: Option[URL], creator: BaseSwaggerEnricherCreator) {

  def enrichers(swaggerServices: List[SwaggerService],
                additionalCategories: List[String],
                fixedParameters: Map[String, () => AnyRef]): Seq[SwaggerEnricherDefinition] = {
    swaggerServices.map { swaggerService =>
      SwaggerEnricherDefinition(
        swaggerService.name,
        swaggerService.documentation,
        swaggerService.categories ++ additionalCategories,
        creator.create(baseUrl, swaggerService, fixedParameters)
      )
    }
  }
}

final case class SwaggerEnricherDefinition(name: String, documentation: Option[String], categories: List[String], service: EagerService)

