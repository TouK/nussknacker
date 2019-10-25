package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2Authenticator(configuration: OAuth2Configuration, service: OAuth2Service) extends SecurityDirectives.AsyncAuthenticator[LoggedUser] with LazyLogging {
  def apply(credentials: Credentials): Future[Option[LoggedUser]] = Future(
    credentials match {
      case c @ Provided(token) => authorize(token)
      case _ => None
    }
  )

  private[security] def authorize(token: String): Option[LoggedUser] = {
    Option.apply(LoggedUser(token, Map("Default" -> Permission.values), isAdmin = true))
//    service.clientApi.doProfileRequest(token).flatMap { resp =>
//
//    }
  }
}

object OAuth2Authenticator extends LazyLogging {
  def apply(configuration: OAuth2Configuration, service: OAuth2Service): OAuth2Authenticator
    = new OAuth2Authenticator(configuration, service)
}