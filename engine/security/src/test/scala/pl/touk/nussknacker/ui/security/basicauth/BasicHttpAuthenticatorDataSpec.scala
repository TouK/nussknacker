package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.Credentials
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.ui.security.{AuthenticationMethod, DefaultAuthenticationConfiguration}
import pl.touk.nussknacker.ui.security.AuthenticationMethod.AuthenticationMethod
import pl.touk.nussknacker.ui.security.DefaultAuthenticationConfiguration.DefaultConfigUser

class BasicHttpAuthenticatorDataSpec extends FunSpec with Matchers {
  class DummyConfiguration(usersList: List[DefaultConfigUser], method: AuthenticationMethod = AuthenticationMethod.BasicAuth, usersFile: String = "")
    extends DefaultAuthenticationConfiguration(method: AuthenticationMethod, usersFile: String) {
    override lazy val users: List[DefaultConfigUser] = usersList
  }

  it("should authenticate using plain password") {
    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(DefaultConfigUser("foo", Some("password"), None, Map.empty))))
    authenticator.authenticate(new SampleProvidedCredentials("foo","password")) shouldBe 'defined
    authenticator.authenticate(new SampleProvidedCredentials("foo","password2")) shouldBe 'empty
  }

  it("should authenticate using bcrypt password") {
    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(DefaultConfigUser("foo", None,
      // result of python -c 'import bcrypt; print(bcrypt.hashpw("password".encode("utf8"), bcrypt.gensalt(rounds=12, prefix="2a")))'
      Some("$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"),
      Map.empty))))
    authenticator.authenticate(new SampleProvidedCredentials("foo","password")) shouldBe 'defined
    authenticator.authenticate(new SampleProvidedCredentials("foo","password2")) shouldBe 'empty
  }

  class SampleProvidedCredentials(identifier: String, receivedSecret: String) extends Credentials.Provided(identifier) {
    def verify(secret: String, hasher: String ⇒ String): Boolean = secret == hasher(receivedSecret)
  }
}