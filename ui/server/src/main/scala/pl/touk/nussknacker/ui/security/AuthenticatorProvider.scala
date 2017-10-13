package pl.touk.nussknacker.ui.security

import java.util.ServiceLoader

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Multiplicity, One}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth

import scala.util.{Failure, Success, Try}

object AuthenticatorProvider {

  import scala.collection.JavaConverters._

  def apply(config: Config, classLoader: ClassLoader): LoggedUserAuth = {
    chooseAuthenticator(
      default = BasicAuthenticatorFactory(),
      loaded = ServiceLoader.load(classOf[AuthenticatorFactory], classLoader)
        .asScala
        .toList) match {
      case Success(auth) => auth.createAuthenticator(config)
      case Failure(e) => throw e
    }
  }

  private[security] def chooseAuthenticator(default: AuthenticatorFactory,
                                            loaded: List[AuthenticatorFactory]): Try[AuthenticatorFactory] = {
    (Multiplicity(loaded), default) match {
      case (One(only), _) => Success(only)
      case (Empty(), default_) => Success(default_)
      case _ => Failure(new IllegalArgumentException(s"default: $default, loaded: $loaded"))
    }
  }
}
