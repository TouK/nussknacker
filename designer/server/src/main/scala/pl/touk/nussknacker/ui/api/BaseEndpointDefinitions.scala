package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir._

trait BaseEndpointDefinitions {

  private def baseNuApiEndpoint = endpoint//.in("api") // todo: explanation

  protected def baseNuApiPublicEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint

  type SecuredCommonUserEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] = Endpoint[LoggedUser, INPUT, ERROR_OUTPUT, OUTPUT, R]

  protected def baseNuApiUserSecuredEndpoint(loggedUser: LoggedUser): SecuredCommonUserEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint
      .securityIn(auth.apiKey(extractFromRequest(_ => loggedUser)))

}
