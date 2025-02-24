package pl.touk.nussknacker.ui.security.oidc

import org.apache.pekko.http.javadsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.apache.pekko.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.typesafe.config.ConfigFactory
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.model.{HeaderNames, Uri}

import java.net.URI
import java.time.Clock
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class GenericOidcTokenAuthenticationSpec
    extends AnyFunSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with FailFastCirceSupport
    with OptionValues {

  implicit val clock: Clock = Clock.systemUTC()

  private val secret      = "secret"
  private val userinfoUri = Uri(URI.create("http://authorization.server/userinfo"))
  private val audience    = "http://nussknacker"

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(5 seconds)

  private val roleClaim = "http://namespace/roles"

  private val config = ConfigFactory.parseString(s"""authentication: {
       |  method: "Oidc"
       |  usersFile: "classpath:oauth2-users.conf"
       |  issuer: "http://ignored"
       |  authorizationEndpoint: "http://ignored"
       |  tokenEndpoint: "http://ignored"
       |  userinfoEndpoint: "$userinfoUri"
       |  clientId: "ignored"
       |  clientSecret: "$secret"
       |  audience: "$audience"
       |  rolesClaims: ["$roleClaim"]
       |}""".stripMargin)

  private val validAccessToken =
    JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresIn(180), secret, JwtAlgorithm.HS256)
  private val expiredAccessToken =
    JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresNow, secret, JwtAlgorithm.HS256)
  private val accessTokenWithInvalidAudience =
    JwtCirce.encode(JwtClaim().about("admin").to("invalid").expiresIn(180), secret, JwtAlgorithm.HS256)

  private val noProfileAccessToken = {
    val claim = JwtClaim()
      .about("no-profile-user")
      .to(audience)
      .withContent(s"""{"$roleClaim": ["User"]}""".stripMargin)
      .expiresIn(180)
    JwtCirce.encode(claim, secret, JwtAlgorithm.HS256)
  }

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
      responseAs[(String, Set[String])] shouldEqual ("SomeConfiguredUsername", Set("Admin", "User"))
    }
  }

}
