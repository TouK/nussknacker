package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticationProvider extends AuthenticationProvider with LazyLogging {

  override def createAuthenticationResources(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    val configuration = OAuth2Configuration.create(config)
    val service = OAuth2ServiceProvider(configuration, classLoader, allCategories)
    new OAuth2AuthenticationResources(realm, service, configuration)
  }
}

object OAuth2AuthenticationProvider {
  def apply(): OAuth2AuthenticationProvider = new OAuth2AuthenticationProvider()
}
