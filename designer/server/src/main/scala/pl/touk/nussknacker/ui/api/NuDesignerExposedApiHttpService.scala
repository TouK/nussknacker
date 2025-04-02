package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.NuDesignerExposedApiHttpService.prependPathForCustomHttpServicePath
import pl.touk.nussknacker.ui.customhttpservice.TapirCustomHttpServiceProvider
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

class NuDesignerExposedApiHttpService(
    customHttpServiceProviders: Map[String, TapirCustomHttpServiceProvider],
    services: BaseHttpService*,
) {

  private val apiEndpoints = services.flatMap(_.serverEndpoints) ++ customHttpServiceEndpoints

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

  def allEndpoints: List[ServerEndpoint[PekkoStreams with WebSockets, Future]] = {
    swaggerEndpoints ::: apiEndpoints.toList
  }

  private def customHttpServiceEndpoints: Iterable[ServerEndpoint[PekkoStreams with WebSockets, Future]] = {
    customHttpServiceProviders.flatMap { case (name, provider) =>
      provider.serverEndpoints.map(prependPathForCustomHttpServicePath(name, _))
    }
  }

}

object NuDesignerExposedApiHttpService {

  val openApiDocumentTitle = "Nussknacker Designer API"

  val openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default
    .copy(markOptionsAsNullable = true)

  private def prependPathForCustomHttpServicePath[R, F[_]](
      name: String,
      serverEndpoint: ServerEndpoint[R, F]
  ): ServerEndpoint[R, F] = {
    ServerEndpoint(
      endpoint = serverEndpoint.endpoint.prependIn("api" / "custom" / name),
      securityLogic = serverEndpoint.securityLogic,
      logic = serverEndpoint.logic,
    )
  }

}
