package pl.touk.nussknacker.ui.security.basicauth

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationProvider extends AuthenticationProvider {

  override val name: String = BasicAuthenticationConfiguration.name

  override def createAuthenticationResources(
      config: Config,
      classLoader: ClassLoader
  )(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): AuthenticationResources = {
    val configuration = BasicAuthenticationConfiguration.create(config)
    new BasicAuthenticationResources(name, configuration)
  }

}

object BasicAuthenticationProvider {
  def apply(): BasicAuthenticationProvider = new BasicAuthenticationProvider()
}
