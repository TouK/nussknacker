package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth

case class OAuth2AuthenticatorFactory() extends AuthenticatorFactory with LazyLogging {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import net.ceedubs.ficus.Ficus._

  import pl.touk.nussknacker.ui.security.AuthenticationConfiguration._

  override def createAuthenticator(config: Config): LoggedUserAuth = {
    createAuthenticator(config.as[OAuth2Configuration]("authentication"))
  }

  def createAuthenticator(conf: OAuth2Configuration): LoggedUserAuth = {
    SecurityDirectives.authenticateOAuth2Async("nussknacker", OAuth2Authenticator())
  }
}

object OAuth2AuthenticatorFactory {
  def create(): OAuth2AuthenticatorFactory = OAuth2AuthenticatorFactory()
}
