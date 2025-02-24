package pl.touk.nussknacker.ui.security.basicauth

import org.apache.pekko.http.scaladsl.server.directives.Credentials
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigUser

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global

class BasicHttpAuthenticationResourcesSpec extends AnyFunSpec with Matchers {

  class DummyConfiguration(
      usersList: List[ConfigUser],
      usersFile: URI = URI.create("classpath:basicauth-user.conf"),
      cachingHashes: Option[CachingHashesConfig] = None
  ) extends BasicAuthenticationConfiguration(usersFile: URI, cachingHashes) {
    override lazy val users: List[ConfigUser] = usersList
  }

  // result of python -c 'import bcrypt; print(bcrypt.hashpw("password".encode("utf8"), bcrypt.gensalt(rounds=12, prefix="2a")))'
  private val encryptedPassword         = "$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"
  private val matchingSecret            = "password"
  private val notMatchingSecret         = "password2"
  private val userWithEncryptedPassword = ConfigUser("foo", None, None, Some(encryptedPassword), Set.empty)

  it("should authenticate using plain password") {
    val authenticator = new BasicHttpAuthenticator(
      new DummyConfiguration(List(ConfigUser("foo", None, Some(matchingSecret), None, Set.empty)))
    )
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe Symbol("defined")
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", notMatchingSecret)) shouldBe Symbol("empty")
  }

  it("should authenticate using bcrypt password") {
    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(userWithEncryptedPassword)))
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe Symbol("defined")
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", notMatchingSecret)) shouldBe Symbol("empty")
  }

  it("should authenticate using bcrypt password with 2y identifier") {
    // result of python -c 'from passlib.hash import bcrypt; print(bcrypt.using(rounds=12, ident="2y").hash("password"))'
    val encryptedPasswordWithPrefix2y = "$2y$12$Lg.AtiNDoHJp1mUD6POPMeqJwh8R/naTrKstlZ76Yn3iGYmAyuWhy"
    val userWithEncryptedPasswordWithPrefix2y =
      ConfigUser("foo", None, None, Some(encryptedPasswordWithPrefix2y), Set.empty)

    val authenticator = new BasicHttpAuthenticator(new DummyConfiguration(List(userWithEncryptedPasswordWithPrefix2y)))
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe Symbol("defined")
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", notMatchingSecret)) shouldBe Symbol("empty")
  }

  it("should cache hashes") {
    var hashComputationCount = 0
    val authenticator = new BasicHttpAuthenticator(
      new DummyConfiguration(
        List(userWithEncryptedPassword),
        cachingHashes = Some(CachingHashesConfig(enabled = Some(true), None, None, None))
      )
    ) {
      override protected def computeBCryptHash(receivedSecret: String, encryptedPassword: String): String = {
        hashComputationCount += 1
        super.computeBCryptHash(receivedSecret, encryptedPassword)
      }
    }
    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe Symbol("defined")
    hashComputationCount shouldEqual 1

    authenticator.doAuthenticate(new SampleProvidedCredentials("foo", matchingSecret)) shouldBe Symbol("defined")
    hashComputationCount shouldEqual 1
  }

  class SampleProvidedCredentials(identifier: String, receivedSecret: String) extends Credentials.Provided(identifier) {
    def verify(secret: String, hasher: String => String): Boolean    = secret == hasher(receivedSecret)
    override def provideVerify(verifier: String => Boolean): Boolean = false
  }

}
