package pl.touk.nussknacker.ui.security.api

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationProvider
import pl.touk.nussknacker.ui.security.oauth2.OAuth2AuthenticationProvider
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationProvider {
  val realm = "nussknacker"

  //TODO: Extract putting allCategories in up level. Authenticator should return only Authenticated User(id, roles)
  // mapping Authenticated User with all Categories should be do only at one place
  def createAuthenticationResources(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationResources
}

object AuthenticationProvider extends LazyLogging {
  def apply(config: Config, classLoader: ClassLoader, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): AuthenticationProvider = {
    val loaded = ScalaServiceLoader.loadClass[AuthenticationProvider](classLoader) {
      AuthenticationConfiguration.parseMethod(config) match {
        case AuthenticationMethod.OAuth2 => OAuth2AuthenticationProvider()
        case _ => BasicAuthenticationProvider()
      }
    }
    logger.info(s"Loaded authenticator method: $loaded.")
    loaded
  }
}


