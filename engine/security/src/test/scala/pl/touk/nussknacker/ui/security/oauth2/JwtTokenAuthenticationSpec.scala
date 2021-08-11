package pl.touk.nussknacker.ui.security.oauth2

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{FunSpec, Matchers}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client.testing.SttpBackendStub
import sttp.model.Uri

import java.net.URI
import java.security.KeyPairGenerator
import java.time.Clock
import java.util.Base64

class JwtTokenAuthenticationSpec extends FunSpec with Matchers with ScalatestRouteTest with Directives with FailFastCirceSupport {

  implicit val clock: Clock = Clock.systemUTC()

  private val keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair()

  private val config = ConfigFactory.parseString(
    s"""
        authentication: {
          method: "OAuth2"
          usersFile: "classpath:oauth2-users.conf"
          authorizeUri: "http://ignored"
          clientSecret: "ignored"
          clientId: "ignored"
          profileUri: "http://authorization.server/userinfo"
          accessTokenUri: "http://authorization.server/token"
          redirectUri: "http://ignored"
          jwt: {
            accessTokenIsJwt: true
            publicKey: "${Base64.getEncoder.encodeToString(keyPair.getPublic.getEncoded)}"
            audience: "http://nussknacker"
          }
        }
      """.stripMargin)

  private val userinfoUri = Uri(URI.create(config.getString("authentication.profileUri")))
  private val audience = config.getString("authentication.jwt.audience")

  private val validAccessToken = JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)
  private val accessTokenWithInvalidAudience = JwtCirce.encode(JwtClaim().about("admin").to("invalid").expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)

  implicit private val testingBackend: RecordingSttpBackend = new RecordingSttpBackend(SttpBackendStub.asynchronousFuture[Nothing]
    .whenRequestMatches(_.uri.equals(userinfoUri))
    .thenRespond(s""" { "sub": "admin" } """))

  private val classLoader = getClass.getClassLoader

  private val authenticationResources = AuthenticationResources(config, classLoader)
  assert(authenticationResources.isInstanceOf[OAuth2AuthenticationResources])

  private val testRoute = Route.seal(
    authenticationResources.authenticate() { authenticatedUser =>
      path("config") {
        authorize(authenticatedUser.roles.contains("Admin")) {
          get {
            complete(authenticatedUser.roles.mkString)
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
    Get("/config").addCredentials(HttpCredentials.createOAuth2BearerToken(accessTokenWithInvalidAudience)) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

}
