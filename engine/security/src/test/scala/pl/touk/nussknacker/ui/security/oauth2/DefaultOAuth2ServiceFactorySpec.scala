package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.DefaultAccessTokenResponse
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2CompoundException, OAuth2ServerError}
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.{ExecutionContext, Future}

class DefaultOAuth2ServiceFactorySpec extends FlatSpec with Matchers with PatientScalaFutures with Suite  {
  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  val config = ExampleOAuth2ServiceFactory.testConfig

  def createErrorOAuth2Service(uri: URI, code: StatusCode) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespondWrapped(Future(Response(Option.empty, code)))

    DefaultOAuth2ServiceFactory.service(config, List.empty)
  }

  def createDefaultServiceMock(body: Json, uri: URI): OAuth2Service = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespond(body.toString)

    DefaultOAuth2ServiceFactory.service(config, List.empty)
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
      case ex@OAuth2CompoundException(errors) => errors.toList.collectFirst {
        case _: OAuth2ServerError => succeed
      }.getOrElse(throw ex)
    }.futureValue
  }

  it should ("properly parse data from profile for profile type User") in {
    val response: Map[String, String] = Map("id" -> "1", "email" -> "some@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id.toString shouldBe response.get("id").get

    user.can("Category1", Permission.Read) shouldBe true
    user.can("Category1", Permission.Write) shouldBe true
    user.can("Category2", Permission.Read) shouldBe true
    user.can("Category2", Permission.Write) shouldBe true
  }

  it should ("properly parse data from profile for profile type UserWithAdminTab") in {
    val response: Map[String, String] = Map("id" -> "1", "email" -> "example2@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id.toString shouldBe response.get("id").get

    user.can("Category1", Permission.Read) shouldBe true
    user.can("Category1", Permission.Write) shouldBe true
    user.can("Category2", Permission.Read) shouldBe true
    user.can("Category2", Permission.Write) shouldBe true
    user.can("Category2", Permission.Deploy) shouldBe true
    user.can("StandaloneCategory1", Permission.Read) shouldBe true
    user.can("StandaloneCategory1", Permission.Write) shouldBe true
    user.can("StandaloneCategory1", Permission.Deploy) shouldBe true
  }

  it should ("properly parse data from profile for profile type Admin") in {

    val response: Map[String, String] = Map("id" -> "1", "email" -> "example@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe true
    user.id shouldBe response.get("id").get


    user.can("Category1", Permission.Read) shouldBe true
    user.can("Category1", Permission.Write) shouldBe true
    user.can("Category1", Permission.Deploy) shouldBe true
    user.can("Category2", Permission.Read) shouldBe true
    user.can("Category2", Permission.Write) shouldBe true
    user.can("Category2", Permission.Deploy) shouldBe true
    user.can("StandaloneCategory1", Permission.Read) shouldBe true
    user.can("StandaloneCategory1", Permission.Write) shouldBe true
    user.can("StandaloneCategory1", Permission.Deploy) shouldBe true
  }

  it should ("properly parse data from profile for profile without email") in {
    val response: Map[String, String] = Map("id" -> "1")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.authorize("6V1reBXblpmfjRJP").futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id shouldBe response.get("id").get

    user.can("Category1", Permission.Read) shouldBe true
    user.can("Category1", Permission.Write) shouldBe true
    user.can("Category2", Permission.Read) shouldBe true
    user.can("Category2", Permission.Write) shouldBe true
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
