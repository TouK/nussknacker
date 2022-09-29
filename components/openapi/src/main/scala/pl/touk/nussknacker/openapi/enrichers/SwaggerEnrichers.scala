package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.EagerService
import pl.touk.nussknacker.openapi.SwaggerService

import java.net.URL

class SwaggerEnrichers(definitionUrl: URL, rootUrl: Option[URL], creator: SwaggerEnricherCreator) {

  def enrichers(swaggerServices: List[SwaggerService],
                additionalCategories: List[String],
                fixedParameters: Map[String, () => AnyRef]): Seq[SwaggerEnricherDefinition] = {
    swaggerServices.map { swaggerService =>
      SwaggerEnricherDefinition(
        swaggerService.name,
        swaggerService.documentation,
        swaggerService.categories ++ additionalCategories,
        creator.create(definitionUrl, rootUrl, swaggerService, fixedParameters)
      )
    }
  }
}

final case class SwaggerEnricherDefinition(name: String, documentation: Option[String], categories: List[String], service: EagerService)

