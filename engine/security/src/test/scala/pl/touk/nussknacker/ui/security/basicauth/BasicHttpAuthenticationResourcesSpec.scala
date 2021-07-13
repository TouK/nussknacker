package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.Credentials
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.{ConfigRule, ConfigUser}

import java.net.URI


class BasicHttpAuthenticationResourcesSpec extends FunSpec with Matchers {
  class DummyConfiguration(usersList: List[ConfigUser], rulesList: List[ConfigRule] = List.empty,
                           usersFile: URI = URI.create("classpath:basicauth-user.conf"), cachingHashes: Option[CachingHashesConfig] = None)
    extends BasicAuthenticationConfiguration(usersFile: URI, cachingHashes) {
    override lazy val users: List[ConfigUser] = usersList
  }

  // result of python -c 'import bcrypt; print(bcrypt.hashpw("password".encode("utf8"), bcrypt.gensalt(rounds=12, prefix="2a")))'
  private val encryptedPassword = "$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"
  private val matchingSecret = "password"
  private val notMatchingSecret = "password2"
  private val userWithEncryptedPassword = ConfigUser("foo", None, Some(encryptedPassword), List.empty)

  it("should authenticate using plain password") {
    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(ConfigUser("foo", Some("password"), None, List.empty))))
    authenticator.authenticate(new SampleProvidedCredentials("foo","password")) shouldBe 'defined
    authenticator.authenticate(new SampleProvidedCredentials("foo","password2")) shouldBe 'empty
  }

  it("should authenticate using bcrypt password") {
    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(userWithEncryptedPassword)))
    authenticator.authenticate(new SampleProvidedCredentials("foo",matchingSecret)) shouldBe 'defined
    authenticator.authenticate(new SampleProvidedCredentials("foo",notMatchingSecret)) shouldBe 'empty
  }

  it("should cache hashes") {
    var hashComputationCount = 0
    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(userWithEncryptedPassword), cachingHashes = Some(CachingHashesConfig(enabled = Some(true), None, None, None)))) {
      override protected def computeBCryptHash(receivedSecret: String, encryptedPassword: String): String = {
        hashComputationCount += 1
        super.computeBCryptHash(receivedSecret, encryptedPassword)
      }
    }
    authenticator.authenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe 'defined
    hashComputationCount shouldEqual 1

    authenticator.authenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe 'defined
    hashComputationCount shouldEqual 1
  }

  class SampleProvidedCredentials(identifier: String, receivedSecret: String) extends Credentials.Provided(identifier) {
    def verify(secret: String, hasher: String ⇒ String): Boolean = secret == hasher(receivedSecret)
  }
}