package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{HttpCookie, `Set-Cookie`}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.FrontendStrategySettings
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client3.Response
import sttp.client3.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.Future
import scala.language.higherKinds

class OAuth2AuthenticationResourcesSpec
    extends AnyFunSpec
    with Matchers
    with ScalatestRouteTest
    with FailFastCirceSupport
    with EitherValues {

  private val defaultConfig = ExampleOAuth2ServiceFactory.testConfig

  private val accessToken = "AH4k6h6KuYaLGfTCdbPayK8HzfM4atZm"

  private val authorizationCode = "B5FwrdqF9cLxwdhL"

  private def routes(oauth2Resources: OAuth2AuthenticationResources) = oauth2Resources.routeWithPathPrefix

  private lazy val errorAuthenticationResources = {
    implicit val testingBackend: RecordingSttpBackend[Future, Any] = new RecordingSttpBackend[Future, Any](
      SttpBackendStub.asynchronousFuture
        .whenRequestMatches(_.uri.equals(Uri(defaultConfig.accessTokenUri)))
        .thenRespond(Response(Option.empty, StatusCode.InternalServerError, "Bad Request"))
    )

    new OAuth2AuthenticationResources(
      defaultConfig.name,
      DefaultOAuth2ServiceFactory.service(defaultConfig),
      defaultConfig
    )
  }

  protected lazy val badAuthenticationResources: OAuth2AuthenticationResources = {
    implicit val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(defaultConfig.accessTokenUri)))
      .thenRespond(Response(Option.empty, StatusCode.BadRequest, "Bad Request"))

    new OAuth2AuthenticationResources(
      defaultConfig.name,
      DefaultOAuth2ServiceFactory.service(defaultConfig),
      defaultConfig
    )
  }

  private def authenticationResources(config: OAuth2Configuration = defaultConfig) = {
    implicit val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(
        s""" {"access_token": "$accessToken", "token_type": "Bearer", "refresh_token": "yFLU8w5VZtqjYrdpD5K9s27JZdJuCRrL"} """
      )
      .whenRequestMatches(_.uri.equals(Uri(config.profileUri)))
      .thenRespond(""" { "id": "1", "login": "aUser", "email": "some@email.com" } """)

    new OAuth2AuthenticationResources(config.name, DefaultOAuth2ServiceFactory.service(config), config)
  }

  private def authenticationOauth2(resource: OAuth2AuthenticationResources, authorizationCode: String) = {
    Get(s"/authentication/oauth2?code=$authorizationCode&redirect_uri=http://some.url/") ~> routes(resource)
  }

  it("should return 400 for wrong authorize token") {
    authenticationOauth2(badAuthenticationResources, authorizationCode) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[Map[String, String]].toString should include(
        "Retrieving access token error. Please try authenticate again."
      )
    }
  }

  it("should return 500 for application error") {
    authenticationOauth2(errorAuthenticationResources, authorizationCode) ~> check {
      status shouldBe StatusCodes.InternalServerError
    }
  }

  it("should redirect for good authorization token") {
    authenticationOauth2(authenticationResources(), authorizationCode) ~> check {
      status shouldBe StatusCodes.OK
      header[`Set-Cookie`] shouldBe None
      val response = responseAs[Json]

      response.hcursor.downField("accessToken").as[String].value should be(accessToken)
      response.hcursor.downField("access_token").as[String].value should be(accessToken)
      response.hcursor.downField("tokenType").as[String].value should be("Bearer")
      response.hcursor.downField("token_type").as[String].value should be("Bearer")
    }
  }

  it("should set cookie in response if configured") {
    val cookieConfig = TokenCookieConfig("customCookie", Some("/myPath"), None)
    authenticationOauth2(
      authenticationResources(config = defaultConfig.copy(tokenCookie = Some(cookieConfig))),
      authorizationCode
    ) ~> check {
      status shouldBe StatusCodes.OK
      header[`Set-Cookie`] shouldBe Some(
        `Set-Cookie`(
          HttpCookie(
            name = cookieConfig.name,
            value = accessToken,
            httpOnly = true,
            secure = true,
            path = cookieConfig.path
          )
        )
      )
    }
  }

  it("should return authentication settings") {
    Get(s"/authentication/oauth2/settings") ~> routes(authenticationResources()) ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Json]
      response.hcursor
        .downField("authorizeUrl")
        .as[String]
        .value shouldBe s"${defaultConfig.authorizeUri}?client_id=${defaultConfig.clientId}"
      response.hcursor.downField("jwtIdTokenNonceVerificationRequired").as[Boolean].value shouldBe false
      response.hcursor.downField("implicitGrantEnabled").as[Boolean].value shouldBe false
      response.hcursor.downField("anonymousAccessAllowed").as[Boolean].value shouldBe false
      response.hcursor.downField("strategy").as[String].value shouldBe "OAuth2"
    }
  }

  it("should return overriden authentication settings") {
    val frontendSettings = FrontendStrategySettings.Remote("http://some.remote.url")
    Get(s"/authentication/oauth2/settings") ~> routes(
      authenticationResources(defaultConfig.copy(overrideFrontendAuthenticationStrategy = Some(frontendSettings)))
    ) ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Json]
      response.hcursor.downField("moduleUrl").as[String].value shouldBe frontendSettings.moduleUrl
    }
  }

}
