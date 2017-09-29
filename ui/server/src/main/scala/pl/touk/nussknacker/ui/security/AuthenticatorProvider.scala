package pl.touk.nussknacker.ui.security

import java.util.ServiceLoader

import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Multiplicity, One}
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, LoggedUser}

import scala.util.{Failure, Success, Try}

object AuthenticatorProvider {

  import scala.collection.JavaConverters._

  def apply(config: Config, classLoader: ClassLoader): SecurityDirectives.Authenticator[LoggedUser] = {
    chooseAuthenticator(
      default = SimpleAuthenticatorFactory(),
      loaded = ServiceLoader.load(classOf[AuthenticatorFactory], classLoader)
        .asScala
        .toSeq) match {
      case Success(auth) => auth.createAuthenticator(config)
      case Failure(e) => throw e
    }
  }

  private[security] def chooseAuthenticator(default: AuthenticatorFactory,
                                            loaded: Seq[AuthenticatorFactory]): Try[AuthenticatorFactory] = {
    (Multiplicity(loaded), default) match {
      case (One(only), _) => Success(only)
      case (Empty(), default_) => Success(default_)
      case _ => Failure(new IllegalArgumentException(s"default: $default, loaded: $loaded"))
    }
  }
}
