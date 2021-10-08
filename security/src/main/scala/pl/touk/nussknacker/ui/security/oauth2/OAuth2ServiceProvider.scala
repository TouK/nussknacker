package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, LoggedUser}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

object OAuth2ServiceProvider extends LazyLogging {
  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] = {
    val service = ScalaServiceLoader.loadClass[OAuth2ServiceFactory](classLoader) {
      DefaultOAuth2ServiceFactory()
    }

    logger.info(s"Loaded OAuth2Service: $service.")

    service.create(configuration)
  }
}
