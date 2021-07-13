package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.Directives
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationProvider extends AuthenticationProvider with Directives {

  override def createAuthenticationResources(config: Config, classLoader: ClassLoader)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    val configuration = BasicAuthenticationConfiguration.create(config)
    new BasicAuthenticationResources(realm, configuration)
  }

  def name: String = BasicAuthenticationConfiguration.name
}

object BasicAuthenticationProvider {
  def apply(): BasicAuthenticationProvider = new BasicAuthenticationProvider()
}