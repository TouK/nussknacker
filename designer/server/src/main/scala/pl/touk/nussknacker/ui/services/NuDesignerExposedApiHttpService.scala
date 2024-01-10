package pl.touk.nussknacker.ui.services

import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

class NuDesignerExposedApiHttpService(
    appApiHttpService: AppApiHttpService,
    componentsApiHttpService: ComponentApiHttpService,
    userApiHttpService: UserApiHttpService,
    notificationApiHttpService: NotificationApiHttpService,
    nodesApiHttpService: NodesApiHttpService
) {

  private val apiEndpoints =
    appApiHttpService.serverEndpoints ++
      componentsApiHttpService.serverEndpoints ++
      userApiHttpService.serverEndpoints ++
      notificationApiHttpService.serverEndpoints ++
      nodesApiHttpService.serverEndpoints

  private val endpointDefinitions = apiEndpoints.map(_.endpoint)

  private val swaggerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(
        pathPrefix = "api" :: "docs" :: Nil,
        yamlName = "nu-designer-openapi.yaml"
      ),
      openAPIInterpreterOptions = NuDesignerExposedApiHttpService.openAPIDocsOptions
    ).fromEndpoints(
      endpointDefinitions,
      NuDesignerExposedApiHttpService.openApiDocumentTitle,
      "" // we don't want to have versioning of this API yet
    )

  def allEndpoints: List[ServerEndpoint[Any, Future]] = {
    swaggerEndpoints ::: apiEndpoints
  }

}

object NuDesignerExposedApiHttpService {

  val openApiDocumentTitle = "Nussknacker Designer API"

  val openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default
    .copy(markOptionsAsNullable = true)
}
