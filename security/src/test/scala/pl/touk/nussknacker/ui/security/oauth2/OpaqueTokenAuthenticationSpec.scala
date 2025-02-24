package pl.touk.nussknacker.ui.security.oauth2

import org.apache.pekko.http.javadsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Method, Uri}

import java.net.URI
import scala.concurrent.Future

class OpaqueTokenAuthenticationSpec
    extends AnyFunSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with FailFastCirceSupport
    with EitherValues {

  private val tokenUri    = Uri(URI.create("http://authorization.server/token"))
  private val userinfoUri = Uri(URI.create("http://authorization.server/userinfo"))

  private val config = ConfigFactory.parseString(s"""authentication: {
       |  method: "OAuth2"
       |  usersFile: "classpath:oauth2-users.conf"
       |  authorizeUri: "http://ignored"
       |  clientSecret: "ignored"
       |  clientId: "ignored"
       |  profileUri: "${userinfoUri}"
       |  profileFormat: "oidc"
       |  accessTokenUri: "${tokenUri}"
       |}""".stripMargin)

  private val validAccessToken = "aValidAccessToken"

  implicit private val testingBackend: RecordingSttpBackend[Future, Any] = new RecordingSttpBackend(
    SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(tokenUri))
      .thenRespond(s""" { "access_token": "${validAccessToken}", "token_type": "Bearer" } """)
      .whenRequestMatches(_.uri.equals(userinfoUri))
      .thenRespond(s""" { "sub": "admin" } """)
  )

  private val classLoader = getClass.getClassLoader

  private val authenticationResources = AuthenticationResources(config, classLoader, testingBackend)
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

  it("should request authorization on anonymous access to a restricted resource") {
    Get("/config") ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should permit an authorized user to a restricted resource") {
    Get(
      "/authentication/oauth2?code=test&redirect_uri=http://ignored/"
    ) ~> authenticationResources.routeWithPathPrefix ~> check {
      status shouldEqual StatusCodes.OK
      val accessToken = responseAs[Json].hcursor.downField("accessToken").as[String].value
      Get("/config").addCredentials(HttpCredentials.createOAuth2BearerToken(accessToken)) ~> testRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  it("should request authorization on an unknown token") {
    testingBackend.clear()
    Get("/config").addCredentials(HttpCredentials.createOAuth2BearerToken("unknown")) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
    // We should deny a token even if it is valid and permit retrieving user info if it has not been obtained before and stored in the cache.
    testingBackend.allInteractions
      .map { case (request, _) => (request.method, request.uri) } should not contain ((Method.GET, userinfoUri))
  }

}
