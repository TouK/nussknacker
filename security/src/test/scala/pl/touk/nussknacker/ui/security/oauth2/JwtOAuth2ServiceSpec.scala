package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.config.ConfigFactory
import io.circe.Decoder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import pl.touk.nussknacker.ui.security.oidc.{DefaultOidcAuthorizationData, OidcUserInfo}
import sttp.client3.testing.SttpBackendStub
import sttp.model.Uri

import java.net.URI
import java.security.KeyPairGenerator
import java.time.{Clock, Instant}
import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WithJwtOauth2Service {
  protected val keyPair   = KeyPairGenerator.getInstance("RSA").generateKeyPair()
  private val userinfoUri = Uri(URI.create("http://authorization.server/userinfo"))
  protected val audience  = "http://nussknacker"

  protected val config = OAuth2Configuration.create(ConfigFactory.parseString(s"""authentication: {
       |  method: "OAuth2"
       |  usersFile: "classpath:oauth2-users.conf"
       |  authorizeUri: "http://ignored"
       |  clientSecret: "ignored"
       |  clientId: "ignored"
       |  profileUri: "${userinfoUri}"
       |  profileFormat: "oidc"
       |  accessTokenUri: "http://authorization.server/token"
       |  jwt: {
       |    accessTokenIsJwt: true
       |    publicKey: "${Base64.getEncoder.encodeToString(keyPair.getPublic.getEncoded)}"
       |    audience: "${audience}"
       |  }
       |}""".stripMargin))

  implicit private val testingBackend: RecordingSttpBackend[Future, Any] = new RecordingSttpBackend(
    SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(userinfoUri))
      .thenRespond(s""" { "sub": "admin" } """)
  )

  implicit private val decoder: Decoder[OidcUserInfo] = OidcUserInfo.decoder
  protected val jwtOAuth2Service =
    new JwtOAuth2Service(OAuth2ClientApi[OidcUserInfo, DefaultOidcAuthorizationData](config), config)
}

class JwtOAuth2ServiceSpec extends AnyFunSpec with ScalaFutures with Matchers with WithJwtOauth2Service {

  final override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit val clock: Clock = Clock.systemUTC()

  it("should respect expiration period") {
    val validAccessToken =
      JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)

    val seconds =
      jwtOAuth2Service
        .checkAuthorizationAndAuthenticateUser(validAccessToken)
        .futureValue
        ._2
        .get
        .getEpochSecond - Instant
        .now()
        .getEpochSecond

    seconds should be <= 180L
  }

}
