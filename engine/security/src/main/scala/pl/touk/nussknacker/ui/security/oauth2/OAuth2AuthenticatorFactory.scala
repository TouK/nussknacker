package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, LoggedUser}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.{AuthenticatorData, LoggedUserAuth}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}


class OAuth2AuthenticatorFactory(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]) extends AuthenticatorFactory with LazyLogging {

  override def createAuthenticator(config: Config, classLoader: ClassLoader, allCategories: List[String]): AuthenticatorData = {
    val configuration = OAuth2Configuration.create(config)
    val service = OAuth2ServiceProvider(configuration, classLoader, allCategories)

    AuthenticatorData(
      createDirective(configuration, service),
      configuration,
      createRoutes(service)
    )
  }

  def createDirective(config: OAuth2Configuration, service: OAuth2Service[LoggedUser, _]): LoggedUserAuth = {
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = OAuth2Authenticator(config, service),
      realm = realm
    )
  }

  def createRoutes(service: OAuth2Service[_, OAuth2AuthorizationData]): List[Route] = List(new AuthenticationOAuth2Resources(service).route())
}

object OAuth2AuthenticatorFactory {
  def apply()(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2AuthenticatorFactory = new OAuth2AuthenticatorFactory()
}
