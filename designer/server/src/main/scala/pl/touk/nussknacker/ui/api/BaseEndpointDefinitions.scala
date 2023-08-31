package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir._

trait BaseEndpointDefinitions {

  type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]
  type SecuredEndpoint[I, E, O, -R] = Endpoint[LoggedUser, I, E, O, R]

  protected def baseNuApiEndpoint =
    endpoint.in("api")
}
