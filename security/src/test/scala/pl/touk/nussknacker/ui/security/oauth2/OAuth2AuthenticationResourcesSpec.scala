package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{HttpCookie, `Set-Cookie`}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.Future
import scala.language.higherKinds

class OAuth2AuthenticationResourcesSpec extends AnyFunSpec with Matchers with ScalatestRouteTest with FailFastCirceSupport {

  private val defaultConfig = ExampleOAuth2ServiceFactory.testConfig

  private val realm = "nussknacker"

  private val accessToken = "AH4k6h6KuYaLGfTCdbPayK8HzfM4atZm"

  private val authorizationCode = "B5FwrdqF9cLxwdhL"

  private def routes(oauth2Resources: OAuth2AuthenticationResources) = oauth2Resources.routeWithPathPrefix

  private lazy val errorAuthenticationResources = {
    implicit val testingBackend = new RecordingSttpBackend(
      SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(defaultConfig.accessTokenUri)))
      .thenRespondWrapped(Future(Response(Option.empty, StatusCode.InternalServerError, "Bad Request")))
    )

    new OAuth2AuthenticationResources(defaultConfig.name, realm, DefaultOAuth2ServiceFactory.service(defaultConfig), defaultConfig)
  }

  protected lazy val badAuthenticationResources = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(defaultConfig.accessTokenUri)))
      .thenRespondWrapped(Future(Response(Option.empty, StatusCode.BadRequest, "Bad Request")))

    new OAuth2AuthenticationResources(defaultConfig.name, realm, DefaultOAuth2ServiceFactory.service(defaultConfig), defaultConfig)
  }

  private def authenticationResources(config: OAuth2Configuration = defaultConfig) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(s""" {"access_token": "$accessToken", "token_type": "Bearer", "refresh_token": "yFLU8w5VZtqjYrdpD5K9s27JZdJuCRrL"} """)
      .whenRequestMatches(_.uri.equals(Uri(config.profileUri)))
      .thenRespond(""" { "id": "1", "login": "aUser", "email": "some@email.com" } """)


    new OAuth2AuthenticationResources(config.name, realm, DefaultOAuth2ServiceFactory.service(config), config)
  }

  private def authenticationOauth2(resource: OAuth2AuthenticationResources, authorizationCode: String) = {
    Get(s"/authentication/oauth2?code=$authorizationCode&redirect_uri=http://some.url/") ~> routes(resource)
  }

  it("should return 400 for wrong authorize token") {
    authenticationOauth2(badAuthenticationResources,  authorizationCode) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[Map[String, String]].toString should include("Retrieving access token error. Please try authenticate again.")
    }
  }

  it("should return 500 for application error") {
    authenticationOauth2(errorAuthenticationResources,  authorizationCode) ~> check {
      status shouldBe StatusCodes.InternalServerError
    }
  }

  it("should redirect for good authorization token") {
    authenticationOauth2(authenticationResources(), authorizationCode) ~> check {
      status shouldBe StatusCodes.OK
      header[`Set-Cookie`] shouldBe None
      val response = responseAs[Oauth2AuthenticationResponse]
      response.accessToken shouldEqual accessToken
      response.tokenType shouldEqual "Bearer"
    }
  }

  it("should set cookie in response if configured") {
    val cookieConfig = TokenCookieConfig("customCookie", Some("/myPath"), None)
    authenticationOauth2(authenticationResources(config = defaultConfig.copy(tokenCookie = Some(cookieConfig))), authorizationCode) ~> check {
      status shouldBe StatusCodes.OK
      header[`Set-Cookie`] shouldBe Some(`Set-Cookie`(HttpCookie(name = cookieConfig.name, value = accessToken, httpOnly = true, secure = true, path = cookieConfig.path)))
    }
  }
}
