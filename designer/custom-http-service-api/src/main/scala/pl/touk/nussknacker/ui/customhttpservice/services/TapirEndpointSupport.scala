package pl.touk.nussknacker.ui.customhttpservice.services

import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api.{LoggedUser, SecurityError}
import sttp.tapir.EndpointInput

import scala.concurrent.Future

trait TapirEndpointSupport {

  type LogicResult[BUSINESS_ERROR, RESULT] = Either[Either[BUSINESS_ERROR, SecurityError], RESULT]

  def authEndpointInput: EndpointInput[AuthCredentials]

  def authorizeAdminUser[BUSINESS_ERROR](credentials: AuthCredentials): Future[LogicResult[BUSINESS_ERROR, LoggedUser]]

  def authorizeKnownUser[BUSINESS_ERROR](credentials: AuthCredentials): Future[LogicResult[BUSINESS_ERROR, LoggedUser]]
}
