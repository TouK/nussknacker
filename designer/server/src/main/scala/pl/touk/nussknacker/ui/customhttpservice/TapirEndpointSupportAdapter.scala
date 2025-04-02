package pl.touk.nussknacker.ui.customhttpservice

import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.customhttpservice.services.TapirEndpointSupport
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir.EndpointInput

import scala.concurrent.Future

class TapirEndpointSupportAdapter extends TapirEndpointSupport {

  override def authEndpointInput: EndpointInput[AuthCredentials] = ???

  override def authorizeAdminUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] = ???

  override def authorizeKnownUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] = ???

}
