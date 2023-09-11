package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.tapir.EndpointInput.Auth
import sttp.tapir._

abstract class BaseEndpointDefinitions(auth: Auth[AuthCredentials, _]) {

  private val baseNuApiEndpoint =
    // TODO: when all services are moved to Tapir (including authn & authz), we can uncomment this path here
    endpoint//.in("api")

  val baseNuApiPublicEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint

  type SecuredEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] = Endpoint[AuthCredentials, INPUT, SecuredEndpointError[ERROR_OUTPUT], OUTPUT, R]

  val baseNuApiUserSecuredEndpoint =
    baseNuApiEndpoint.securityIn(auth)

  def allEndpoints: List[AnyEndpoint]
}

sealed trait SecuredEndpointError[+E]
object SecuredEndpointError {
  case object AuthenticationError extends SecuredEndpointError[Nothing]
  case object AuthorizationError extends SecuredEndpointError[Nothing]
  final case class OtherError[E](error: E) extends SecuredEndpointError[E]
}