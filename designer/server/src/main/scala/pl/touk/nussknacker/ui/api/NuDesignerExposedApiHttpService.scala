package pl.touk.nussknacker.ui.services

import pl.touk.nussknacker.ui.api.BaseHttpService
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

class NuDesignerExposedApiHttpService(
    services: BaseHttpService*
) {

  private val apiEndpoints = services.flatMap(_.serverEndpoints)

  private val endpointDefinitions = apiEndpoints.map(_.endpoint)

  private val swaggerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(
        pathPrefix = "api" :: "docs" :: Nil,
        yamlName = "nu-designer-openapi.yaml"
      ),
      openAPIInterpreterOptions = NuDesignerExposedApiHttpService.openAPIDocsOptions
    ).fromEndpoints(
      endpointDefinitions.toList,
      NuDesignerExposedApiHttpService.openApiDocumentTitle,
      "" // we don't want to have versioning of this API yet
    )

  def allEndpoints: List[ServerEndpoint[Any, Future]] = {
    swaggerEndpoints ::: apiEndpoints.toList
  }

}

object NuDesignerExposedApiHttpService {

  val openApiDocumentTitle = "Nussknacker Designer API"

  val openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default
    .copy(markOptionsAsNullable = true)
}
