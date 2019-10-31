package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.oauth2.DefaultOAuth2ServiceFactory.{OAuth2AuthenticateData, OAuth2Profile}

import scala.concurrent.Future

object OAuth2ServiceProvider extends LazyLogging {
  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader): OAuth2Service = {
    val service = ScalaServiceLoader.loadClass[OAuth2ServiceFactory](classLoader) {
      DefaultOAuth2ServiceFactory()
    }

    logger.info(s"Loaded OAuth2Service: $service.")

    service.create(configuration)
  }

  trait OAuth2Service {
    def authenticate(code: String): Future[OAuth2AuthenticateData]
    def profile(token: String): Future[OAuth2Profile]
  }

  trait OAuth2ServiceFactory {
    def create(configuration: OAuth2Configuration): OAuth2Service
  }
}
