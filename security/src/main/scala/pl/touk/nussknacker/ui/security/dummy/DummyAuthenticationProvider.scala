package pl.touk.nussknacker.ui.security.dummy

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources, ImpersonationContext}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class DummyAuthenticationProvider extends AuthenticationProvider {

  override val name: String = "Dummy"

  override def createAuthenticationResources(
      config: Config,
      classLoader: ClassLoader
  )(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): AuthenticationResources = {
    new DummyAuthenticationResources(name, DummyAuthenticationConfiguration.create(config))
  }

  override def createImpersonationContext(config: Config): ImpersonationContext = (_: String) => None
}
