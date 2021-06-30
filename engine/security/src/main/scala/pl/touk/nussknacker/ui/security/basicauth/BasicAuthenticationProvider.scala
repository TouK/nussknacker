package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.Directives
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources, DefaultAuthenticationConfiguration}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationProvider extends AuthenticationProvider with Directives {

  override def createAuthenticationResources(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    val configuration = DefaultAuthenticationConfiguration.create(config)
    new BasicAuthenticationResources(realm, configuration, allCategories)
  }
}

object BasicAuthenticationProvider {
  def apply(): BasicAuthenticationProvider = new BasicAuthenticationProvider()
}