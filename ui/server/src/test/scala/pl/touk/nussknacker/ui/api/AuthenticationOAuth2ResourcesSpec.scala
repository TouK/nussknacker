package pl.touk.nussknacker.ui.api

import java.net.URI

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withoutPermissions
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration
import pl.touk.nussknacker.ui.security.{AuthenticationBackend, OAuth2TestServiceFactory}
import sttp.client.HttpError
import sttp.client.testing.SttpBackendStub
import sttp.model.Uri

import scala.language.higherKinds

class AuthenticationOAuth2ResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with ScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  val oAuth2configuration = OAuth2Configuration(
    AuthenticationBackend.OAuth2,
    "ui/server/develConf/sample/users.conf",
    URI.create("http://demo.nussknacker.pl/oauth/authorize"),
    "clientSecret",
    "clientId",
    URI.create("http://demo.nussknacker.pl/api/user"),
    URI.create("http://demo.nussknacker.pl/oauth/token"),
    URI.create("http://demo.nussknacker.pl/api/authentication/oauth2")
  )

  protected lazy val badAuthenticationResources = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(oAuth2configuration.accessTokenUri)))
      .thenRespond(throw HttpError("Bad authorize token or data"))

    new AuthenticationOAuth2Resources(OAuth2TestServiceFactory(oAuth2configuration))
  }

  protected lazy val authenticationResources = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(oAuth2configuration.accessTokenUri)))
      .thenRespond(""" {"access_token": "AH4k6h6KuYaLGfTCdbPayK8HzfM4atZm", "token_type": "Bearer", "refresh_token": "yFLU8w5VZtqjYrdpD5K9s27JZdJuCRrL"} """)

    new AuthenticationOAuth2Resources(OAuth2TestServiceFactory(oAuth2configuration))
  }

  def authenticationOauth2(resource: AuthenticationOAuth2Resources, authorizeToken: String) = {
    Get(s"/authentication/oauth2?code=$authorizeToken") ~> withoutPermissions(resource)
  }

  it("should return 400 for wrong authorize token") {
    authenticationOauth2(badAuthenticationResources,  "B5FwrdqF9cLxwdhL") ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[Map[String, String]].toString should include("Retrieving access token error. Please contact with system administrators.")
    }
  }

  it("should redirect for good authorize token") {
    authenticationOauth2(authenticationResources, "B5FwrdqF9cLxwdhL") ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Oauth2AuthenticationResponse]
      response.accessToken shouldEqual "AH4k6h6KuYaLGfTCdbPayK8HzfM4atZm"
      response.tokenType shouldEqual "Bearer"
    }
  }
}