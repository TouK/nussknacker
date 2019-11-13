package pl.touk.nussknacker.ui.security.api.oauth2

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.{AuthenticatorData, LoggedUserAuth}
import pl.touk.nussknacker.ui.security.oauth2.{AuthenticationOAuth2Resources, OAuth2Authenticator, OAuth2Configuration, OAuth2Service, OAuth2ServiceProvider}


class OAuth2AuthenticatorFactory extends AuthenticatorFactory with LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def createAuthenticator(config: Config, classLoader: ClassLoader, allCategories: List[String]): AuthenticatorData = {
    val configuration = OAuth2Configuration.create(config)
    val service = OAuth2ServiceProvider(configuration, classLoader, allCategories)

    AuthenticatorData(
      createDirective(configuration, service),
      configuration,
      createRoutes(service)
    )
  }

  def createDirective(config: OAuth2Configuration, service: OAuth2Service): LoggedUserAuth = {
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = OAuth2Authenticator(config, service),
      realm = realm
    )
  }

  def createRoutes(service: OAuth2Service): List[Route] = List(new AuthenticationOAuth2Resources(service).route())
}

object OAuth2AuthenticatorFactory {
  def apply(): OAuth2AuthenticatorFactory = new OAuth2AuthenticatorFactory()
}
