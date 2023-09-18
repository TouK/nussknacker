package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.app.AppApiHttpService
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

class NuDesignerOpenApi(appApiHttpService: AppApiHttpService) {

  val publicServerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(pathPrefix = "api" :: "docs" :: Nil)
    )
      .fromEndpoints(
        appApiHttpService.allEndpointDefinitions,
        "Nussknacker Designer API",
        "1.0.0"
      )
}
