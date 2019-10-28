package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.{Credentials, SecurityDirectives}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Authenticator.OAuth2TokenRejection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2Authenticator(configuration: OAuth2Configuration, service: OAuth2Service) extends SecurityDirectives.AsyncAuthenticator[LoggedUser] with LazyLogging {
  def apply(credentials: Credentials): Future[Option[LoggedUser]] =
    authenticate(credentials)

  private[security] def authenticate(credentials: Credentials): Future[Option[LoggedUser]] = {
    credentials match {
      case Provided(token) => authenticate(token)
      case _ => Future.successful(Option.empty)
    }
  }

  private[security] def authenticate(token: String): Future[Option[LoggedUser]] =
    service.profile(token).map { profile =>
      Option.apply(
        LoggedUser(
          id = profile.id,
          isAdmin = OAuth2AuthenticatorFactory.isAdmin(profile.roles, configuration.rules),
          categoryPermissions = OAuth2AuthenticatorFactory.getPermissions(profile.roles, configuration.rules),
          globalPermissions = OAuth2AuthenticatorFactory.getGlobalPermissions(profile.roles, configuration.rules)
        )
      )
    }.recover {
      case ex: OAuth2TokenRejection => Option.empty // Expired or non-exists token - user not authenticated
    }
}

object OAuth2Authenticator extends LazyLogging {
  case class OAuth2TokenRejection(body: String) extends Exception

  def apply(configuration: OAuth2Configuration, service: OAuth2Service): OAuth2Authenticator
    = new OAuth2Authenticator(configuration, service)
}

