package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.app.AppApiHttpService
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

class NuDesignerExposedApi(appApiHttpService: AppApiHttpService) {

  private val apiEndpoints = appApiHttpService.serverEndpoints
  private val endpointDefinitions = apiEndpoints.map(_.endpoint)

  private val swaggerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(
        pathPrefix = "api" :: "docs" :: Nil,
        yamlName = "nu-designer-openapi.yaml"
      )
    ).fromEndpoints(
      endpointDefinitions,
      NuDesignerAvailableToExposeApi.name,
      NuDesignerAvailableToExposeApi.version
    )

  def allEndpoints: List[ServerEndpoint[Any, Future]] = {
    swaggerEndpoints ::: apiEndpoints
  }
}