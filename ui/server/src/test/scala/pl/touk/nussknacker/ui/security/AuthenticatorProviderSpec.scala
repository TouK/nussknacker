package pl.touk.nussknacker.ui.security

import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.{AuthenticatorFactory, LoggedUser}

class AuthenticatorProviderSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  it should "return default authentication provider" in {
    val conf = ConfigFactory.defaultApplication()
    AuthenticatorProvider(conf, this.getClass.getClassLoader) shouldBe a[LoggedUserAuth]
  }

  object AuthenticatorProviderSpec {
    val u = Tuple1(LoggedUser("u"))

    class DummyAuthenticator extends LoggedUserAuth {
      override def tapply(f: (Tuple1[LoggedUser]) => Route): Route = f(u)
    }

    case class DummyAuthenticatorFactory() extends AuthenticatorFactory {
      override def createAuthenticator(config: Config, classLoader: ClassLoader) = new DummyAuthenticator
    }

    class DummyAuthenticator2 extends LoggedUserAuth {
      override def tapply(f: (Tuple1[LoggedUser]) => Route): Route = f(u)
    }

    case class DummyAuthenticatorFactory2() extends AuthenticatorFactory {
      override def createAuthenticator(config: Config, classLoader: ClassLoader) = new DummyAuthenticator2
    }
  }
}