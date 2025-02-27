package pl.touk.nussknacker.ui.security.oauth2

import org.scalatest.Suite
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.oauth2.ExampleOAuth2ServiceFactory.{
  TestAccessTokenResponse,
  TestProfileClearanceResponse,
  TestProfileResponse
}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2CompoundException, OAuth2ServerError}
import sttp.client3.Response
import sttp.client3.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class ExampleOAuth2ServiceFactorySpec extends AnyFlatSpec with Matchers with PatientScalaFutures with Suite {

  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  val config = ExampleOAuth2ServiceFactory.testConfig

  def createErrorOAuth2Service(uri: URI, code: StatusCode) = {
    implicit val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespond(Response(Option.empty, code))

    ExampleOAuth2ServiceFactory.service(config)
  }

  it should ("properly parse data from authentication") in {
    val tokenResponse = TestAccessTokenResponse(
      accessToken = "9IDpWSEYetSNRX41",
      tokenType = "Bearer",
      expirationPeriod = Some(FiniteDuration(86400, SECONDS))
    )
    val userInfo      = TestProfileResponse("some@e.mail", "uid", TestProfileClearanceResponse(Set("User")))
    val authorizeJson = tokenResponse.asJson.toString
    implicit val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(authorizeJson)
      .whenRequestMatches(_.uri.equals(Uri(config.profileUri)))
      .thenRespond(userInfo.asJson.toString)
    val service = ExampleOAuth2ServiceFactory.service(config)

    val (data, _) = service.obtainAuthorizationAndAuthenticateUser("6V1reBXblpmfjRJP", "http://ignored").futureValue

    data shouldBe a[OAuth2AuthorizationData]
    data.accessToken shouldBe tokenResponse.accessToken
    data.tokenType shouldBe tokenResponse.tokenType
    data.expirationPeriod shouldBe tokenResponse.expirationPeriod
  }

  it should ("handling BadRequest response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.BadRequest)
    service
      .obtainAuthorizationAndAuthenticateUser("6V1reBXblpmfjRJP", "http://ignored")
      .recover { case OAuth2ErrorHandler(_) =>
        succeed
      }
      .futureValue
  }

  it should ("should InternalServerError response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.InternalServerError)
    service
      .obtainAuthorizationAndAuthenticateUser("6V1reBXblpmfjRJP", "http://ignored")
      .recover { case ex @ OAuth2CompoundException(errors) =>
        errors.toList
          .collectFirst { case _: OAuth2ServerError =>
            succeed
          }
          .getOrElse(throw ex)
      }
      .futureValue
  }

  it should ("handling BadRequest response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.BadRequest)
    service
      .checkAuthorizationAndAuthenticateUser("6V1reBXblpmfjRJP")
      .recover { case OAuth2ErrorHandler(_) =>
        succeed
      }
      .futureValue
  }

  it should ("should InternalServerError response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.InternalServerError)
    service
      .checkAuthorizationAndAuthenticateUser("6V1reBXblpmfjRJP")
      .recover { case ex @ OAuth2CompoundException(errors) =>
        errors.toList
          .collectFirst { case _: OAuth2ServerError =>
            succeed
          }
          .getOrElse(throw ex)
      }
      .futureValue
  }

}
