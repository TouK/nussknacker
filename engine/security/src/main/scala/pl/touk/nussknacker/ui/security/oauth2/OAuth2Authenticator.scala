package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class OAuth2Authenticator(configuration: OAuth2Configuration, service: OAuth2Service) extends SecurityDirectives.AsyncAuthenticator[LoggedUser] with LazyLogging {
  def apply(credentials: Credentials): Future[Option[LoggedUser]] =
    authenticate(credentials)

  private[security] def authenticate(credentials: Credentials): Future[Option[LoggedUser]] = {
    credentials match {
      case Provided(token) => authenticate(token)
      case _ => Future.successful(Option.empty)
    }
  }

  private[oauth2] def authenticate(token: String): Future[Option[LoggedUser]] =
    service.authorize(token).map(prf => Option(prf)).recover {
      case OAuth2ErrorHandler(_) => Option.empty // Expired or non-exists token - user not authenticated
    }
}

object OAuth2Authenticator extends LazyLogging {
  def apply(configuration: OAuth2Configuration, service: OAuth2Service): OAuth2Authenticator
    = new OAuth2Authenticator(configuration, service)
}

object OAuth2ErrorHandler {
  case class OAuth2AuthenticationRejection(body: String) extends Exception
  case class OAuth2AccessTokenRejection(body: String) extends Exception
  case class OAuth2ServerError(body: String) extends Exception

  def apply(t: Throwable): Boolean = t match {
    case _: OAuth2AuthenticationRejection | _: OAuth2AccessTokenRejection => true
    case _ => false
  }

  def unapply(t: Throwable): Option[Throwable] = Some(t).filter(apply)
}