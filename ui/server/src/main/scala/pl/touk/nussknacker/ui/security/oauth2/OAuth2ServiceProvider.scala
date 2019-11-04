package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.LoggedUser

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
    def authorize(token: String): Future[LoggedUser]
  }

  trait OAuth2ServiceFactory {
    def create(configuration: OAuth2Configuration): OAuth2Service
  }

  case class OAuth2AuthenticateData(access_token: String, token_type: String, refresh_token: Option[String])
}
