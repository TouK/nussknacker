package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth

class OAuth2AuthenticatorFactory extends AuthenticatorFactory with LazyLogging {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import net.ceedubs.ficus.Ficus._

  import pl.touk.nussknacker.ui.security.AuthenticationConfig._

  override def createAuthenticator(config: Config): LoggedUserAuth = {
    createAuthenticator(config.as[OAuth2Config]("authentication"))
  }

  def createAuthenticator(conf: OAuth2Config): LoggedUserAuth = {
    SecurityDirectives.authenticateOAuth2Async("nussknacker", OAuth2Authenticator())
  }
}

object OAuth2AuthenticatorFactory {

  def apply(): OAuth2AuthenticatorFactory = new OAuth2AuthenticatorFactory()
}
