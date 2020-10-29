package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
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

  def createErrorOAuth2Service(uri: URI, code: StatusCode): ExampleOAuth2Service = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespondWrapped(Future(Response(Option.empty, code)))

    ExampleOAuth2ServiceFactory.service(config)
  }

  def createDefaultServiceMock(body: Json, uri: URI): ExampleOAuth2Service = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespond(body.toString)

    ExampleOAuth2ServiceFactory.service(config)
  }

  it should ("properly parse data from authentication") in {
    val body = TestAccessTokenResponse(access_token = "9IDpWSEYetSNRX41", token_type = "Bearer")
    val service = createDefaultServiceMock(body.asJson, config.accessTokenUri)
    val data = service.authenticate("6V1reBXblpmfjRJP").futureValue

    data shouldBe a[OAuth2AuthenticateData]
    data.access_token shouldBe body.access_token
    data.token_type shouldBe body.token_type
  }

  it should ("handling BadRequest response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.BadRequest)
    service.authenticate("6V1reBXblpmfjRJP").recover {
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.InternalServerError)
    service.authenticate("6V1reBXblpmfjRJP").recover {
      case ex@OAuth2CompoundException(errors) => errors.toList.collectFirst {
        case _: OAuth2ServerError => succeed
      }.getOrElse(throw ex)
    }.futureValue
  }

  it should ("properly parse data from profile for profile Reader role") in {
    val response = TestProfileResponse(
      email = "some@email.com",
      uid = "3123123",
      clearance = TestProfileClearanceResponse(
        roles = List(TestPermissionResponse.Reader.toString),
        portals = List("somePortal1", "somePortal2")
      )
    )

    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id shouldBe response.uid

    user.can("somePortal1", Permission.Read) shouldBe true
    user.can("somePortal2", Permission.Read) shouldBe true
  }

  it should ("properly parse data from profile for profile Deploy, Writer role with access to AdminTab") in {
    val response = TestProfileResponse(
      email = "some@email.com",
      uid = "3123123",
      clearance = TestProfileClearanceResponse(
        roles = List(
          TestPermissionResponse.Deployer.toString,
          TestPermissionResponse.Writer.toString,
          TestPermissionResponse.AdminTab.toString
        ),
        portals = List("somePortal1", "somePortal2")
      )
    )

    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id shouldBe response.uid

    user.can("somePortal1", Permission.Read) shouldBe false
    user.can("somePortal1", Permission.Deploy) shouldBe true
    user.can("somePortal2", Permission.Read) shouldBe false
    user.can("somePortal2", Permission.Deploy) shouldBe true
  }

  it should ("properly parse data from profile for profile role Admin") in {
    val response = TestProfileResponse(
      email = "some@email.com",
      uid = "3123123",
      clearance = TestProfileClearanceResponse(
        roles = List(
          TestPermissionResponse.Admin.toString
        ),
        portals = List("somePortal1")
      )
    )

    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe true
    user.id shouldBe response.uid

    user.can("somePortal1", Permission.Read) shouldBe true
    user.can("somePortal1", Permission.Write) shouldBe true
    user.can("somePortal1", Permission.Deploy) shouldBe true
  }

  it should ("handling BadRequest response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.BadRequest)
    service.authorize("6V1reBXblpmfjRJP").recover{
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.InternalServerError)
    service.authorize("6V1reBXblpmfjRJP").recover{
      case ex@OAuth2CompoundException(errors) => errors.toList.collectFirst {
        case _: OAuth2ServerError => succeed
      }.getOrElse(throw ex)
    }.futureValue
  }
}
