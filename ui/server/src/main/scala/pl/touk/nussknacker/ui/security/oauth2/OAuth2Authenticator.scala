package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2Authenticator extends SecurityDirectives.AsyncAuthenticator[LoggedUser] with LazyLogging {
  def apply(credentials: Credentials): Future[Option[LoggedUser]] = Future(authorize(credentials))

  private[security] def authorize(credentials: Credentials): Option[LoggedUser] = {
    logger.debug(s"Trying authorize.. $credentials")
    credentials match {
      case c @ Provided(token) => Option.apply(LoggedUser(token, Map("Default" -> Permission.values)))
      case _ => None
    }
  }
}

object OAuth2Authenticator extends LazyLogging {
  def apply(): OAuth2Authenticator = new OAuth2Authenticator()
}