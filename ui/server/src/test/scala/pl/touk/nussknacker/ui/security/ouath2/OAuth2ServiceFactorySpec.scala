package pl.touk.nussknacker.ui.security.ouath2

import java.net.URI

import io.circe.Json
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, Permission}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.DefaultAccessTokenResponse
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.OAuth2ServerError
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceFactory.{OAuth2AuthenticateData, OAuth2Profile}
import pl.touk.nussknacker.ui.security.oauth2._
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2ServiceFactorySpec extends FlatSpec with Matchers with ScalaFutures with Suite  {
  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  //Some future takes long time..
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  val config = OAuth2TestServiceFactory.getTestConfig

  def createErrorOAuth2Service(uri: URI, code: StatusCode) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespondWrapped(Future(Response(Option.empty, code)))

    OAuth2ServiceFactory(config)
  }

  def createDefaultServiceMock(body: Json, uri: URI): DefaultOAuth2Service = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespond(body.toString)

    OAuth2ServiceFactory(config)
  }

  it should ("properly parse data from authentication") in {
    val body = DefaultAccessTokenResponse(access_token = "9IDpWSEYetSNRX41", token_type = "Bearer", refresh_token = Option.apply("QZYuU0FVobxg8oCW"))
    val service = createDefaultServiceMock(body.asJson, config.accessTokenUri)
    val data = service.authenticate("6V1reBXblpmfjRJP").futureValue

    data shouldBe a[OAuth2AuthenticateData]
    data.access_token shouldBe body.access_token
    data.token_type shouldBe body.token_type
    data.refresh_token shouldBe body.refresh_token
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

  it should ("properly parse data from profile for profile type User") in {
    val response: Map[String, String] = Map("id" -> "1", "email" -> "some@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.profile("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[OAuth2Profile]
    user.isAdmin shouldBe false
    user.id.toString shouldBe response.get("id").get
    user.roles.contains(OAuth2ServiceFactory.defaultUserRole) shouldBe true
    user.accesses.isEmpty shouldBe true

    user.permissions shouldBe Map(
      "Default" -> Set(Permission.Read, Permission.Write),
      "FraudDetection" -> Set(Permission.Read, Permission.Write)
    )
  }

  it should ("properly parse data from profile for profile type UserWithAdminTab") in {
    val response: Map[String, String] = Map("id" -> "1", "email" -> "example2@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.profile("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[OAuth2Profile]
    user.isAdmin shouldBe false
    user.id.toString shouldBe response.get("id").get
    user.roles.contains(OAuth2ServiceFactory.defaultUserRole) shouldBe true
    user.roles.contains("UserWithAdminTab") shouldBe true
    user.accesses.contains(GlobalPermission.AdminTab)

    user.permissions shouldBe Map(
      "Default" -> Set(Permission.Read, Permission.Write, Permission.Deploy),
      "FraudDetection" -> Set(Permission.Read, Permission.Write),
      "Recommendations" -> Set(Permission.Read, Permission.Write, Permission.Deploy)
    )
  }

  it should ("properly parse data from profile for profile type Admin") in {
    val response: Map[String, String] = Map("id" -> "1", "email" -> "example@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.profile("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[OAuth2Profile]
    user.isAdmin shouldBe true
    user.id.toString shouldBe response.get("id").get
    user.roles.contains(OAuth2ServiceFactory.defaultUserRole) shouldBe true
    user.roles.contains("Admin") shouldBe true
    user.accesses.contains(GlobalPermission.AdminTab)

    user.permissions shouldBe Map(
      "Default" -> Set(Permission.Read, Permission.Write, Permission.Deploy),
      "FraudDetection" -> Set(Permission.Read, Permission.Write, Permission.Deploy),
      "Recommendations" -> Set(Permission.Read, Permission.Write, Permission.Deploy)
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
