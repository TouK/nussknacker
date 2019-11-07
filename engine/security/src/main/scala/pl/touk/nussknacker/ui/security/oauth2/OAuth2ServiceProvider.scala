package pl.touk.nussknacker.ui.security.api.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration

object OAuth2ServiceProvider extends LazyLogging {
  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader, allCategories: List[String] = Nil): OAuth2Service = {
    val service = ScalaServiceLoader.loadClass[OAuth2ServiceFactory](classLoader) {
      DefaultOAuth2ServiceFactory()
    }

    logger.info(s"Loaded OAuth2Service: $service.")

    service.create(configuration, allCategories)
  }
}
