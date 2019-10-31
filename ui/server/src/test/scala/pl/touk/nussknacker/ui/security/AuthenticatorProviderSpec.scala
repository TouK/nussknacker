package pl.touk.nussknacker.ui.security

import com.typesafe.config.ConfigFactory
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.security.api.AuthenticatorFactory.LoggedUserAuth

class AuthenticatorProviderSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {
  it should "return default authentication provider" in {
    val conf = ConfigFactory.defaultApplication()
    AuthenticatorProvider(conf, this.getClass.getClassLoader) shouldBe a[LoggedUserAuth]
  }
}