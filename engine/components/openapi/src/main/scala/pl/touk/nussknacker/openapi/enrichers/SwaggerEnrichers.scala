package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.EagerService
import pl.touk.nussknacker.openapi.SwaggerService

import java.net.URL

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

