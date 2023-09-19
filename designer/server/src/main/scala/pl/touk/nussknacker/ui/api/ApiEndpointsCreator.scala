package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.tapir.EndpointInput.Auth

trait ApiEndpointsCreator[T <: BaseEndpointDefinitions] {

  def create(auth: Auth[AuthCredentials, _]): T
}
