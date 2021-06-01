package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.AuthenticatorData
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticatorFactory extends AuthenticatorFactory with LazyLogging {

  override def createAuthenticator(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticatorData = {
    val configuration = OAuth2Configuration.create(config)
    val service = OAuth2ServiceProvider(configuration, classLoader, allCategories)

    AuthenticatorData(
      directive = SecurityDirectives.authenticateOAuth2Async(
        authenticator = OAuth2Authenticator(configuration, service),
        realm = realm
      ),
      configuration,
      routes = List(new AuthenticationOAuth2Resources(service).route())
    )
  }
}

object OAuth2AuthenticatorFactory {
  def apply(): OAuth2AuthenticatorFactory = new OAuth2AuthenticatorFactory()
}
