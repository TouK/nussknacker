package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode.{Forbidden, Unauthorized}
import sttp.tapir.EndpointInput.Auth
import sttp.tapir._

abstract class BaseEndpointDefinitions(auth: Auth[AuthCredentials, _]) {

  val baseNuApiEndpoint =
    // TODO: when all services are moved to Tapir (including authn & authz), we can uncomment this path here
    endpoint//.in("api")

  val baseNuApiPublicEndpoint =
    baseNuApiEndpoint

  type SecuredEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] =
    Endpoint[AuthCredentials, INPUT, Either[ERROR_OUTPUT, SecurityError], OUTPUT, R]

  val baseNuApiUserSecuredEndpoint: SecuredEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint
      .securityIn(auth)
      .errorOutEither(
        oneOf(
          oneOfVariantFromMatchType(Unauthorized, emptyOutputAs(SecurityError.AuthenticationError)),
          oneOfVariantFromMatchType(Forbidden, emptyOutputAs(SecurityError.AuthorizationError)),
        )
      )

  def allEndpoints: List[AnyEndpoint]

}
object BaseEndpointDefinitions {
  type EndpointError[ERROR] = Either[SecurityError, ERROR]
}

sealed trait SecurityError
object SecurityError {
  case object AuthenticationError extends SecurityError
  case object AuthorizationError extends SecurityError
}
