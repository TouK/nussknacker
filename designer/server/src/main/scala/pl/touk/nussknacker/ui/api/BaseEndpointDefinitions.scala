package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir._

trait BaseEndpointDefinitions {

  private val baseNuApiEndpoint =
    // TODO: when all services are moved to Tapir (including authn & authz), we can uncomment this path here
    endpoint//.in("api")

  protected val baseNuApiPublicEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint

  type SecuredEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] = Endpoint[LoggedUser, INPUT, ERROR_OUTPUT, OUTPUT, R]

  protected def baseNuApiUserSecuredEndpoint(loggedUser: LoggedUser): SecuredEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint
      .securityIn(auth.apiKey(extractFromRequest(_ => loggedUser)))

}
