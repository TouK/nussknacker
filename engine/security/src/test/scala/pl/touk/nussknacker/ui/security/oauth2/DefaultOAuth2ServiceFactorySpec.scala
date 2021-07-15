package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationConfiguration, LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2CompoundException, OAuth2ServerError}
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

class DefaultOAuth2ServiceFactorySpec extends FlatSpec with Matchers with PatientScalaFutures with Suite  {
  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  val config = ExampleOAuth2ServiceFactory.testConfig
  val rules = ExampleOAuth2ServiceFactory.testRules

  def createErrorOAuth2Service(uri: URI, code: StatusCode) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespondWrapped(Future(Response(Option.empty, code)))

    DefaultOAuth2ServiceFactory.service(config)
  }

  def createDefaultServiceMock(body: Json, uri: URI) = {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(uri)))
      .thenRespond(body.toString)

    DefaultOAuth2ServiceFactory.service(config)
  }

  it should ("properly parse data from authentication") in {
    val body = DefaultOAuth2AuthorizationData(accessToken = "9IDpWSEYetSNRX41", tokenType = "Bearer", refreshToken = Option.apply("QZYuU0FVobxg8oCW"))
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(body.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(Map("id" -> "1", "email" -> "some@email.com").asJson.toString())
    val service = DefaultOAuth2ServiceFactory.service(config)
    val (data, _) = service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP").futureValue

    data shouldBe a[OAuth2AuthorizationData]
    data.accessToken shouldBe body.accessToken
    data.tokenType shouldBe body.tokenType
    data.refreshToken shouldBe body.refreshToken
  }

  it should ("handling BadRequest response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.BadRequest)
    service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP").recover{
      case OAuth2ErrorHandler(_) => succeed
    }.futureValue
  }

  it should ("should InternalServerError response from authenticate request") in {
    val service = createErrorOAuth2Service(config.accessTokenUri, StatusCode.InternalServerError)
    service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP").recover{
      case ex@OAuth2CompoundException(errors) => errors.toList.collectFirst {
        case _: OAuth2ServerError => succeed
      }.getOrElse(throw ex)
    }.futureValue
  }

  it should ("properly parse data from profile for profile type User") in {
    val response: Map[String, String] = Map("id" -> "1", "email" -> "some@email.com")
    val service = createDefaultServiceMock(response.asJson, config.profileUri)
    val user = service.checkAuthorizationAndObtainUserinfo("6V1reBXblpmfjRJP")
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

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
    val user = service.checkAuthorizationAndObtainUserinfo("6V1reBXblpmfjRJP")
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

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
    val user = service.checkAuthorizationAndObtainUserinfo("6V1reBXblpmfjRJP")
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

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
    val user = service.checkAuthorizationAndObtainUserinfo("6V1reBXblpmfjRJP")
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

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
