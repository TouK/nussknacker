package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.api.{AuthenticationConfiguration, LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.ExampleOAuth2ServiceFactory.{TestAccessTokenResponse, TestPermissionResponse, TestProfileClearanceResponse, TestProfileResponse}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2CompoundException, OAuth2ServerError}
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.{ExecutionContext, Future}

class ExampleOAuth2ServiceFactorySpec extends FlatSpec with Matchers with PatientScalaFutures with Suite  {
  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  val config = ExampleOAuth2ServiceFactory.testConfig
  val rules = ExampleOAuth2ServiceFactory.testRules

  def createErrorOAuth2Service(uri: URI, code: StatusCode) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespondWrapped(Future(Response(Option.empty, code)))

    ExampleOAuth2ServiceFactory.service(config)
  }

  def createDefaultServiceMock(body: Json, uri: URI) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespond(body.toString)

      ExampleOAuth2ServiceFactory.service(config)
  }

  it should ("properly parse data from authentication") in {
    val body = TestAccessTokenResponse(accessToken = "9IDpWSEYetSNRX41", tokenType = "Bearer")
    val service = createDefaultServiceMock(body.asJson, config.accessTokenUri)
    val (data, _) = service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP").futureValue

    data shouldBe a[OAuth2AuthorizationData]
    data.accessToken shouldBe body.accessToken
    data.tokenType shouldBe body.tokenType
  }

  it should ("handling BadRequest response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.BadRequest)
    service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP").recover {
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.InternalServerError)
    service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP").recover {
      case ex@OAuth2CompoundException(errors) => errors.toList.collectFirst {
        case _: OAuth2ServerError => succeed
      }.getOrElse(throw ex)
    }.futureValue
  }

  it should ("handling BadRequest response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.BadRequest)
    service.checkAuthorizationAndObtainUserinfo("6V1reBXblpmfjRJP").recover{
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.InternalServerError)
    service.checkAuthorizationAndObtainUserinfo("6V1reBXblpmfjRJP").recover{
      case ex@OAuth2CompoundException(errors) => errors.toList.collectFirst {
        case _: OAuth2ServerError => succeed
      }.getOrElse(throw ex)
    }.futureValue
  }
}
