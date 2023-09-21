package pl.touk.nussknacker.ui.services

import pl.touk.nussknacker.ui.services.BaseHttpService.NoRequirementServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

class NuDesignerOpenApiHttpService(appApiHttpService: AppApiHttpService) {

  val publicServerEndpoints: List[NoRequirementServerEndpoint] =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(pathPrefix = "api" :: "docs" :: Nil)
    )
      .fromEndpoints(
        appApiHttpService.allEndpointDefinitions,
        "Nussknacker Designer API",
        "1.0.0"
      )
}
