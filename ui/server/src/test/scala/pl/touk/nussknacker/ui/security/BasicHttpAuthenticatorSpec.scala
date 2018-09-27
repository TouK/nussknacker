package pl.touk.nussknacker.ui.security

import akka.http.scaladsl.server.directives.Credentials
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.ui.security.BasicHttpAuthenticator.ConfiguredUser

class BasicHttpAuthenticatorSpec extends FunSpec with Matchers {

  it("should authorize using plain password") {
    val authenticator = new BasicHttpAuthenticator(List(ConfiguredUser("foo", Some("password"), None, Map.empty)))
    authenticator.authorize(new SampleProvidedCredentials("foo","password")) shouldBe 'defined
    authenticator.authorize(new SampleProvidedCredentials("foo","password2")) shouldBe 'empty
  }

  it("should authorize using bcrypt password") {
    val authenticator = new BasicHttpAuthenticator(List(ConfiguredUser("foo", None,
      // result of python -c 'import bcrypt; print(bcrypt.hashpw("password".encode("utf8"), bcrypt.gensalt(rounds=12, prefix="2a")))'
      Some("$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"),
      Map.empty)))
    authenticator.authorize(new SampleProvidedCredentials("foo","password")) shouldBe 'defined
    authenticator.authorize(new SampleProvidedCredentials("foo","password2")) shouldBe 'empty
  }

  class SampleProvidedCredentials(identifier: String, receivedSecret: String) extends Credentials.Provided(identifier) {
    def verify(secret: String, hasher: String ⇒ String): Boolean = secret == hasher(receivedSecret)
  }

}