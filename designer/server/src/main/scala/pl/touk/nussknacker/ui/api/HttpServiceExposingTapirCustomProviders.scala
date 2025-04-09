package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.ui.customhttpservice.TapirCustomHttpServiceProvider
import pl.touk.nussknacker.ui.security.api.AuthManager
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir._
import sttp.tapir.PublicEndpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

class HttpServiceExposingTapirCustomProviders(
    customHttpServiceProviders: Map[String, TapirCustomHttpServiceProvider],
    authManager: AuthManager,
)(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime)
    extends BaseHttpService(authManager) {

  expose {
    customHttpServiceEndpoints
  }

  private def customHttpServiceEndpoints: List[ServerEndpoint[PekkoStreams, Future]] = {
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
    }.toList
  }

  private def addSecurity[INPUT, BUSINESS_ERROR, OUTPUT, R](
      endpoint: PublicEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]
  ): SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R] = {
    new BaseEndpointDefinitions.ToSecure(endpoint).withSecurity(authManager.authenticationEndpointInput())
  }

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
