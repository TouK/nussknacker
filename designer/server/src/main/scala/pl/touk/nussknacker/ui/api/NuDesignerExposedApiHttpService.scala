package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.BaseHttpService.LogicResult
import pl.touk.nussknacker.ui.api.NuDesignerExposedApiHttpService.prependPathForCustomHttpServicePath
import pl.touk.nussknacker.ui.customhttpservice.TapirCustomHttpServiceProvider
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.language.higherKinds

class NuDesignerExposedApiHttpService(
    customHttpServiceProviders: Map[String, TapirCustomHttpServiceProvider],
    authManager: AuthManager,
    services: BaseHttpService*,
)(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime) {

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

  private val httpServiceSupport = new HttpServiceSupport(authManager)

  def allEndpoints: List[ServerEndpoint[PekkoStreams with WebSockets, Future]] = {
    swaggerEndpoints ::: apiEndpoints.toList
  }

  private def customHttpServiceEndpoints: Iterable[ServerEndpoint[PekkoStreams with WebSockets, Future]] = {
    customHttpServiceProviders.flatMap { case (name, provider) =>
      val endpoints = provider.serverEndpointDefinitions.map { endpointDefinition =>
        addSecurity(endpointDefinition.definition)
          .serverSecurityLogic(authorizeKnownUser[endpointDefinition.ERROR])
          .serverLogic(user =>
            request =>
              endpointDefinition
                .logic(user, request)
                .map {
                  case Left(businessError) => Left(Left(businessError))
                  case Right(value)        => Right(value)
                }
                .unsafeToFuture()(executionContextWithIORuntime.ioRuntime)
          )
      }
      endpoints.map(prependPathForCustomHttpServicePath(name, _))
    }
  }

  private def addSecurity[INPUT, BUSINESS_ERROR, OUTPUT, R](
      endpoint: PublicEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]
  ): SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R] = {
    new BaseEndpointDefinitions.ToSecure(endpoint).withSecurity(authManager.authenticationEndpointInput())
  }

  private def authorizeKnownUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] =
    httpServiceSupport.authorizeKnownUser(credentials)

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
