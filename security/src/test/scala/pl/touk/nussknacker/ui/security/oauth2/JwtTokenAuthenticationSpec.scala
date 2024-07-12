package pl.touk.nussknacker.ui.security.oauth2

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.model.{HeaderNames, Uri}

import java.net.URI
import java.security.KeyPairGenerator
import java.time.Clock
import java.util.Base64
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class JwtTokenAuthenticationSpec
    extends AnyFunSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with FailFastCirceSupport
    with OptionValues {

  implicit val clock: Clock = Clock.systemUTC()

  private val keyPair     = KeyPairGenerator.getInstance("RSA").generateKeyPair()
  private val userinfoUri = Uri(URI.create("http://authorization.server/userinfo"))
  private val audience    = "http://nussknacker"

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(5 seconds)

  private val config = ConfigFactory.parseString(s"""authentication: {
       |  method: "OAuth2"
       |  usersFile: "classpath:oauth2-users.conf"
       |  authorizeUri: "http://ignored"
       |  clientSecret: "ignored"
       |  clientId: "ignored"
       |  profileUri: "$userinfoUri"
       |  profileFormat: "oidc"
       |  accessTokenUri: "http://authorization.server/token"
       |  jwt: {
       |    accessTokenIsJwt: true
       |    publicKey: "${Base64.getEncoder.encodeToString(keyPair.getPublic.getEncoded)}"
       |    audience: "$audience"
       |  },
       |  realm: "nussknacker-test"
       |}""".stripMargin)

  private val validAccessToken =
    JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)
  private val expiredAccessToken =
    JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresNow, keyPair.getPrivate, JwtAlgorithm.RS256)
  private val accessTokenWithInvalidAudience =
    JwtCirce.encode(JwtClaim().about("admin").to("invalid").expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)

  private val noProfileAccessToken = JwtCirce.encode(
    JwtClaim().about("no-profile-user").to(audience).expiresIn(180),
    keyPair.getPrivate,
    JwtAlgorithm.RS256
  )

  implicit private val testingBackend: RecordingSttpBackend[Future, Any] = new RecordingSttpBackend(
    SttpBackendStub.asynchronousFuture
      .whenRequestMatches(req =>
        req.uri == userinfoUri && req.header(HeaderNames.Authorization).value != s"Bearer $noProfileAccessToken"
      )
      .thenRespond(s""" { "sub": "admin" } """)
  )
  // See classpath:oauth2-users.conf for the roles defined for user admin.

  private val classLoader = getClass.getClassLoader

  private val authenticationResources = AuthenticationResources(config, classLoader, testingBackend)
  assert(authenticationResources.isInstanceOf[OAuth2AuthenticationResources])

  private val testRoute = Route.seal(
    authenticationResources.authenticate() { authenticatedUser =>
      path("config") {
        authorize(authenticatedUser.roles.contains("Admin")) {
          get {
            complete((authenticatedUser.username, authenticatedUser.roles))
          }
        }
      }
    }
  )

  it("should permit an authorized user to a restricted resource") {
    Get("/config").addCredentials(HttpCredentials.createOAuth2BearerToken(validAccessToken)) ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  it("should request authorization on a token with an invalid audience claim") {
    Get("/config").addCredentials(
      HttpCredentials.createOAuth2BearerToken(accessTokenWithInvalidAudience)
    ) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should request authorization on expired token") {
    Get("/config").addCredentials(HttpCredentials.createOAuth2BearerToken(expiredAccessToken)) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should not call for user profile when subject has username configured in user configuration") {
    Get("/config").addCredentials(HttpCredentials.createOAuth2BearerToken(noProfileAccessToken)) ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[(String, Set[String])] shouldEqual ("SomeConfiguredUsername", Set("Admin"))
    }
  }

  it("should use custom realm") {
    Get("/config") ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
      header("WWW-Authenticate").map(_.value()) shouldEqual Some("Bearer realm=\"nussknacker-test\"")
    }
  }

}
