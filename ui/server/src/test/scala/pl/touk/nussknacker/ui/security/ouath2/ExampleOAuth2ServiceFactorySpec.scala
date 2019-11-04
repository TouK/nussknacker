package pl.touk.nussknacker.ui.security.ouath2

import java.net.URI

import io.circe.Json
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, Permission}
import pl.touk.nussknacker.ui.security.oauth2.{DefaultOAuth2ServiceFactory, OAuth2ErrorHandler}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.OAuth2ServerError
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceProvider.{OAuth2AuthenticateData, OAuth2Profile}
import pl.touk.nussknacker.ui.security.ouath2.ExampleOAuth2ServiceFactory.{TestAccessTokenResponse, TestPermissionResponse, TestProfileClearanceResponse, TestProfileResponse}
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.{ExecutionContext, Future}

class ExampleOAuth2ServiceFactorySpec extends FlatSpec with Matchers with ScalaFutures with Suite  {
  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  //Some future takes long time..
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

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
    service.authenticate("6V1reBXblpmfjRJP").recover{
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.InternalServerError)
    service.authenticate("6V1reBXblpmfjRJP").recover{
      case _: OAuth2ServerError => succeed
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
    val user = service.profile("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[OAuth2Profile]
    user.isAdmin shouldBe false
    user.id shouldBe response.uid
    user.roles.contains(TestPermissionResponse.Reader.toString) shouldBe true
    user.roles.contains(TestPermissionResponse.Writer.toString) shouldBe false
    user.roles.contains(TestPermissionResponse.Deployer.toString) shouldBe false
    user.accesses.isEmpty shouldBe true

    user.permissions shouldBe Map(
      "somePortal1" -> Set(Permission.Read),
      "somePortal2" -> Set(Permission.Read)
    )
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
    val user = service.profile("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[OAuth2Profile]
    user.isAdmin shouldBe false
    user.id shouldBe response.uid
    user.roles.contains(TestPermissionResponse.Deployer.toString) shouldBe true
    user.roles.contains(TestPermissionResponse.Writer.toString) shouldBe true
    user.roles.contains(TestPermissionResponse.Reader.toString) shouldBe false
    user.roles.contains(TestPermissionResponse.AdminTab.toString) shouldBe true
    user.accesses.contains(GlobalPermission.AdminTab)

    user.permissions shouldBe Map(
      "somePortal1" -> Set(Permission.Deploy, Permission.Write),
      "somePortal2" -> Set(Permission.Deploy, Permission.Write)
    )
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
    val user = service.profile("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[OAuth2Profile]
    user.isAdmin shouldBe true
    user.id shouldBe response.uid
    user.roles.contains(TestPermissionResponse.Admin.toString) shouldBe true
    user.accesses.contains(GlobalPermission.AdminTab)

    user.permissions shouldBe Map(
      "somePortal1" -> Set(Permission.Read, Permission.Write, Permission.Deploy)
    )
  }

  it should ("handling BadRequest response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.BadRequest)
    service.profile("6V1reBXblpmfjRJP").recover{
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from profile request") in {
    val service = createErrorOAuth2Service(config.profileUri, StatusCode.InternalServerError)
    service.profile("6V1reBXblpmfjRJP").recover{
      case _: OAuth2ServerError => succeed
    }.futureValue
  }
}
