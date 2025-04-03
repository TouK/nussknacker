package pl.touk.nussknacker.ui.customhttpservice.services

import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.customhttpservice.services.TapirEndpointSupport.{LogicResult, SecuredEndpoint}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, SecurityError}
import sttp.tapir.{Endpoint, EndpointInput, PublicEndpoint}

import scala.concurrent.Future

trait TapirEndpointSupport {

  def success[RESULT](value: RESULT): Right[Nothing, RESULT] = Right(value)

  def businessError[BUSINESS_ERROR](error: BUSINESS_ERROR): Left[Left[BUSINESS_ERROR, Nothing], Nothing] =
    Left(Left(error))

  def authorizeAdminUser[BUSINESS_ERROR](credentials: AuthCredentials): Future[LogicResult[BUSINESS_ERROR, LoggedUser]]

  def authorizeKnownUser[BUSINESS_ERROR](credentials: AuthCredentials): Future[LogicResult[BUSINESS_ERROR, LoggedUser]]

  protected def withSecurity_[INPUT, BUSINESS_ERROR, OUTPUT, R](
      endpoint: PublicEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]
  ): SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]

  implicit class ToSecure[INPUT, BUSINESS_ERROR, OUTPUT, -R](
      val endpoint: PublicEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]
  ) {

    def secure: SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R] = {
      withSecurity_(endpoint)
    }

  }

}

object TapirEndpointSupport {
  type LogicResult[BUSINESS_ERROR, RESULT] = Either[Either[BUSINESS_ERROR, SecurityError], RESULT]

  type SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, -R] =
    Endpoint[AuthCredentials, INPUT, Either[BUSINESS_ERROR, SecurityError], OUTPUT, R]
}
