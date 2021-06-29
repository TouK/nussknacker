package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticationResources.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources, DefaultAuthenticationConfiguration}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationProvider extends AuthenticationProvider with Directives {

  override def createAuthenticationResources(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    val configuration = DefaultAuthenticationConfiguration.create(config)
    AuthenticationResources(createDirective(configuration, allCategories), configuration)
  }

  def createDirective(config: DefaultAuthenticationConfiguration, allCategories: List[String]): LoggedUserAuth =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(config, allCategories),
      realm = realm
    )
}

object BasicAuthenticationProvider {
  def apply(): BasicAuthenticationProvider = new BasicAuthenticationProvider()
}