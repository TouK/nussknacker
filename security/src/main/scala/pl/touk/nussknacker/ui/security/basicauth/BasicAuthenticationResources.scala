package pl.touk.nussknacker.ui.security.basicauth

import org.apache.pekko.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.api.AuthenticationResources.defaultRealm
import pl.touk.nussknacker.ui.security.api._
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationResources(
    override val name: String,
    override val configuration: BasicAuthenticationConfiguration
)(
    implicit executionContext: ExecutionContext
) extends AuthenticationResources {

  override type CONFIG = BasicAuthenticationConfiguration

  private val authenticator = BasicHttpAuthenticator(configuration)

  override protected val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  override def authenticate(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = authenticator,
      realm = realm
    )

  override def authenticate(authCredentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(authCredentials)
  }

  override def authenticationMethod(): EndpointInput[Option[PassedAuthCredentials]] =
    auth
      .basic[Option[String]](WWWAuthenticateChallenge.basic.realm(realm))
      .map(_.map(PassedAuthCredentials))(_.map(_.value))

  override def impersonationSupport: ImpersonationSupport = new ImpersonationSupported {

    override def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData] =
      configuration.users
        .find { _.identity == impersonatedUserIdentity }
        .flatMap { configUser =>
          configUser.username match {
            case Some(username) => Some(ImpersonatedUserData(configUser.identity, username, configUser.roles))
            case None => Some(ImpersonatedUserData(configUser.identity, configUser.identity, configUser.roles))
          }
        }

  }

  override def getAnonymousRole: Option[String] = configuration.anonymousUserRole

  private def realm = configuration.realm.getOrElse(defaultRealm)
}
