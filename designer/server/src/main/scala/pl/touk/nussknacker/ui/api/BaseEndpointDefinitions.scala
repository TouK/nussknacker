package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir._

trait BaseEndpointDefinitions {

  private def baseNuApiEndpoint = endpoint//.in("api") // todo: explanation

  protected def baseNuApiPublicEndpoint: PublicEndpoint[Unit, Unit, Unit, Unit] =
    baseNuApiEndpoint

  type SecuredEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] = Endpoint[LoggedUser, INPUT, ERROR_OUTPUT, OUTPUT, R]

  protected def baseNuApiSecuredEndpoint(loggedUser: LoggedUser): SecuredEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint
      .securityIn(auth.apiKey(extractFromRequest(_ => loggedUser)))
}
