package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.EagerService
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, ServiceName, SwaggerService}
import sttp.model.StatusCode

object SwaggerEnrichers {

  def prepare(config: OpenAPIServicesConfig,
              swaggerServices: List[SwaggerService],
              creator: SwaggerEnricherCreator): Seq[SwaggerEnricherDefinition] = {
    val additionalCategories = Nil
    //TODO: add configuration
    val fixedParameters = Map[String, () => AnyRef]()
    swaggerServices.map { swaggerService =>
      SwaggerEnricherDefinition(
        swaggerService.name,
        swaggerService.documentation,
        swaggerService.categories ++ additionalCategories,
        creator.create(config.url, config.rootUrl, swaggerService, fixedParameters, config.codesToInterpretAsEmpty.map(StatusCode(_)))
      )
    }
  }
}

final case class SwaggerEnricherDefinition(name: ServiceName, documentation: Option[String], categories: List[String], service: EagerService)

