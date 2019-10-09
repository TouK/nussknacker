package pl.touk.nussknacker.ui.security

import java.util.ServiceLoader

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Multiplicity, One}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticatorFactory
import pl.touk.nussknacker.ui.security.oauth2.OAuth2AuthenticatorFactory

import scala.util.{Failure, Success, Try}

object AuthenticatorProvider extends LazyLogging {

  import scala.collection.JavaConverters._

  def apply(config: Config, authenticationConfig: AuthenticationConfig, classLoader: ClassLoader): LoggedUserAuth = {
    val default = getDefaultAuthenticatorFactory(authenticationConfig.getBackend())

    logger.info(s"Default authenticator backend: $default")

    chooseAuthenticator(
      default = default,
      loaded = ServiceLoader.load(classOf[AuthenticatorFactory], classLoader).asScala.toList
    ) match {
      case Success(auth) => auth.createAuthenticator(config)
      case Failure(e) => throw e
    }
  }

  private [security] def getDefaultAuthenticatorFactory(backend: AuthenticationBackend.Value): AuthenticatorFactory = {
    backend match {
      case AuthenticationBackend.BasicAuth => BasicAuthenticatorFactory()
      case AuthenticationBackend.OAuth2 => OAuth2AuthenticatorFactory()
    }
  }

  private[security] def chooseAuthenticator(default: AuthenticatorFactory, loaded: List[AuthenticatorFactory]): Try[AuthenticatorFactory] = {
    (Multiplicity(loaded), default) match {
      case (One(only), _) => Success(only)
      case (Empty(), default_) => Success(default_)
      case _ => Failure(new IllegalArgumentException(s"default: $default, loaded: $loaded"))
    }
  }
}
