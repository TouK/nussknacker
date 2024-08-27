package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticationProvider extends AuthenticationProvider with LazyLogging {

  override val name: String = OAuth2Configuration.name

  override def createAuthenticationResources(
      config: Config,
      classLoader: ClassLoader
  )(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): AuthenticationResources = {
    val configuration = OAuth2Configuration.create(config)
    val service       = OAuth2ServiceProvider(configuration, classLoader)
    new OAuth2AuthenticationResources(name, service, configuration)
  }

}

object OAuth2AuthenticationProvider {
  def apply(): OAuth2AuthenticationProvider = new OAuth2AuthenticationProvider()
}
