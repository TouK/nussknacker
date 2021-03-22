package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.ui.security.http.RecordingSttpBackend
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.Future
import scala.language.higherKinds

class AuthenticationOAuth2ResourcesSpec extends FunSpec with Matchers with ScalatestRouteTest with FailFastCirceSupport {

  val config = ExampleOAuth2ServiceFactory.testConfig

  def routes(route: AuthenticationOAuth2Resources) = route.route()

  protected lazy val errorAuthenticationResources = {
    implicit val testingBackend = new RecordingSttpBackend(
      SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespondWrapped(Future(Response(Option.empty, StatusCode.InternalServerError, "Bad Request")))
    )

    new AuthenticationOAuth2Resources(DefaultOAuth2ServiceFactory.service(config, List.empty))
  }

  protected lazy val badAuthenticationResources = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespondWrapped(Future(Response(Option.empty, StatusCode.BadRequest, "Bad Request")))

    new AuthenticationOAuth2Resources(DefaultOAuth2ServiceFactory.service(config, List.empty))
  }

  protected lazy val authenticationResources = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(""" {"access_token": "AH4k6h6KuYaLGfTCdbPayK8HzfM4atZm", "token_type": "Bearer", "refresh_token": "yFLU8w5VZtqjYrdpD5K9s27JZdJuCRrL"} """)
      .whenRequestMatches(_.uri.equals(Uri(config.profileUri)))
      .thenRespond(""" { "id": "1", "email": "some@email.com" } """)


    new AuthenticationOAuth2Resources(DefaultOAuth2ServiceFactory.service(config, List.empty))
  }

  def authenticationOauth2(resource: AuthenticationOAuth2Resources, authorizationCode: String) = {
    Get(s"/authentication/oauth2?code=$authorizationCode") ~> routes(resource)
  }

  it("should return 400 for wrong authorize token") {
    authenticationOauth2(badAuthenticationResources,  "B5FwrdqF9cLxwdhL") ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[Map[String, String]].toString should include("Retrieving access token error. Please try authenticate again.")
    }
  }

  it("should return 500 for application error") {
    authenticationOauth2(errorAuthenticationResources,  "B5FwrdqF9cLxwdhL") ~> check {
      status shouldBe StatusCodes.InternalServerError
    }
  }

  it("should redirect for good authorization token") {
    authenticationOauth2(authenticationResources, "B5FwrdqF9cLxwdhL") ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Oauth2AuthenticationResponse]
      response.accessToken shouldEqual "AH4k6h6KuYaLGfTCdbPayK8HzfM4atZm"
      response.tokenType shouldEqual "Bearer"
    }
  }
}
