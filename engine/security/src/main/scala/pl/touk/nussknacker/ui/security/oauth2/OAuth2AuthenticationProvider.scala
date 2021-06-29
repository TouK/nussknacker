package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources, LoggedUser}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticationProvider extends AuthenticationProvider with LazyLogging {

  def createDirective(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationDirective[LoggedUser] = {
    val configuration = OAuth2Configuration.create(config)
    val service = OAuth2ServiceProvider(configuration, classLoader, allCategories)
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = OAuth2Authenticator(configuration, service),
      realm = realm
    )
  }

  override def createAuthenticationResources(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    val configuration = OAuth2Configuration.create(config)
    val service = OAuth2ServiceProvider(configuration, classLoader, allCategories)
    AuthenticationResources(
      directive = createDirective(config, classLoader, allCategories),
      configuration,
      route = new AuthenticationOAuth2Resources(service).route()
    )
  }
}

object OAuth2AuthenticationProvider {
  def apply(): OAuth2AuthenticationProvider = new OAuth2AuthenticationProvider()
}
