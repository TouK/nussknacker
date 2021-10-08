package pl.touk.nussknacker.ui.security.dummy

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources, FrontendStrategySettings}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class DummyAuthenticationProvider extends AuthenticationProvider {
  def createAuthenticationResources(config: Config, classLoader: ClassLoader)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources = {
    new DummyAuthenticationResources(name, DummyAuthenticationConfiguration.create(config))
  }

  def name: String = "Dummy"
}
