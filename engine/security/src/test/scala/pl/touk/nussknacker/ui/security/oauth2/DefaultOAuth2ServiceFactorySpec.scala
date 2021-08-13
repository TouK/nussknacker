package pl.touk.nussknacker.ui.security.oauth2

import org.scalatest.{FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2CompoundException, OAuth2ServerError}
import sttp.client.Response
import sttp.client.testing.SttpBackendStub
import sttp.model.{StatusCode, Uri}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Random, Success}

class DefaultOAuth2ServiceFactorySpec extends FlatSpec with Matchers with PatientScalaFutures with Suite  {
  import io.circe.syntax._

  import ExecutionContext.Implicits.global

  val config = ExampleOAuth2ServiceFactory.testConfig
  val rules = ExampleOAuth2ServiceFactory.testRules

  val validAuthorizationData: DefaultOAuth2AuthorizationData =
    DefaultOAuth2AuthorizationData(accessToken = Random.nextString(10), tokenType = "Bearer")
  val validAdminUserInfo: GitHubProfileResponse =
    GitHubProfileResponse(id = 1, email = Some("admin@email.com"), login = "admin")
  val validUserInfo: GitHubProfileResponse =
    GitHubProfileResponse(id = 2, email = Some("user@email.com"), login = "user")
  val validUserInfoWithoutEmail: GitHubProfileResponse =
    GitHubProfileResponse(id = 2, email = None, login = "user")
  val validUserWithAdminTabInfo: GitHubProfileResponse =
    GitHubProfileResponse(id = 3, email = Some("userWithAdminTab@email.com"), login = "userWithAdminTab")

  it should ("properly parse data from authentication") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(validUserInfo.asJson.toString())
    val service = DefaultOAuth2ServiceFactory.service(config)
    val (data, _) = service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP", "http://ignored").futureValue

    data shouldBe a[OAuth2AuthorizationData]
    data.accessToken shouldBe validAuthorizationData.accessToken
    data.tokenType shouldBe validAuthorizationData.tokenType
    data.refreshToken shouldBe validAuthorizationData.refreshToken
  }

  it should ("handling BadRequest response from authenticate request") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(Response(None, StatusCode.BadRequest))
    val service = DefaultOAuth2ServiceFactory.service(config)
    service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP", "http://ignored")
      .transform {
        case Failure(OAuth2CompoundException(_)) => Success(succeed)
        case _ => Failure(fail())
      }
      .futureValue
  }

  it should ("should InternalServerError response from authenticate request") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(Response(None, StatusCode.InternalServerError))
    val service = DefaultOAuth2ServiceFactory.service(config)
    service.obtainAuthorizationAndUserInfo("6V1reBXblpmfjRJP", "http://ignored")
      .transform {
        case Failure(OAuth2CompoundException(errors)) if errors.toList.exists(_.isInstanceOf[OAuth2ServerError]) => Success(succeed)
        case _ => Failure(fail())
      }
      .futureValue
  }

  it should ("properly parse data from profile for profile type User") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(validUserInfo.asJson.toString())
    val service = DefaultOAuth2ServiceFactory.service(config)

    val user = service.obtainAuthorizationAndUserInfo("code", "http://ignored")
      .flatMap { case (authorizationData, _) => service.checkAuthorizationAndObtainUserinfo(authorizationData.accessToken) }
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id shouldBe validUserInfo.id.toString

    user.can("Category1", Permission.Read) shouldBe true
    user.can("Category1", Permission.Write) shouldBe true
    user.can("Category2", Permission.Read) shouldBe true
    user.can("Category2", Permission.Write) shouldBe true
  }

  it should ("properly parse data from profile for profile type UserWithAdminTab") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(validUserWithAdminTabInfo.asJson.toString())
    val service = DefaultOAuth2ServiceFactory.service(config)

    val user = service.obtainAuthorizationAndUserInfo("code", "http://ignored")
      .flatMap { case (authorizationData, _) => service.checkAuthorizationAndObtainUserinfo(authorizationData.accessToken) }
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id shouldBe validUserWithAdminTabInfo.id.toString

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
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(validAdminUserInfo.asJson.toString())
    val service = DefaultOAuth2ServiceFactory.service(config)

    val user = service.obtainAuthorizationAndUserInfo("code", "http://ignored")
      .flatMap { case (authorizationData, _) => service.checkAuthorizationAndObtainUserinfo(authorizationData.accessToken) }
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe true
    user.id shouldBe validAdminUserInfo.id.toString

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
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(validUserInfoWithoutEmail.asJson.toString())
    val service = DefaultOAuth2ServiceFactory.service(config)

    val user = service.obtainAuthorizationAndUserInfo("code", "http://ignored")
      .flatMap { case (authorizationData, _) => service.checkAuthorizationAndObtainUserinfo(authorizationData.accessToken) }
      .map { case (user, _) => LoggedUser(user, rules, List.empty) }.futureValue

    user shouldBe a[LoggedUser]
    user.isAdmin shouldBe false
    user.id shouldBe validUserInfoWithoutEmail.id.toString

    user.can("Category1", Permission.Read) shouldBe true
    user.can("Category1", Permission.Write) shouldBe true
    user.can("Category2", Permission.Read) shouldBe true
    user.can("Category2", Permission.Write) shouldBe true
  }

  it should ("handling BadRequest response from profile request") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(Response(None, StatusCode.BadRequest))
    val service = DefaultOAuth2ServiceFactory.service(config)

    service.obtainAuthorizationAndUserInfo("code", "http://ignored")
      .flatMap { case (authorizationData, _) => service.checkAuthorizationAndObtainUserinfo(authorizationData.accessToken) }
      .transform {
        case Failure(OAuth2CompoundException(_)) => Success(succeed)
        case _ => Failure(fail())
      }
      .futureValue
  }

  it should ("should InternalServerError response from profile request") in {
    implicit val testingBackend = SttpBackendStub
      .asynchronousFuture
      .whenRequestMatches(_.uri.equals(Uri(config.accessTokenUri)))
      .thenRespond(validAuthorizationData.asJson.toString())
      .whenRequestMatches(_.uri.equals((Uri(config.profileUri))))
      .thenRespond(Response(None, StatusCode.InternalServerError))
    val service = DefaultOAuth2ServiceFactory.service(config)

    service.obtainAuthorizationAndUserInfo("code", "http://ignored")
      .flatMap { case (authorizationData, _) => service.checkAuthorizationAndObtainUserinfo(authorizationData.accessToken) }
      .transform {
        case Failure(OAuth2CompoundException(errors)) if errors.toList.exists(_.isInstanceOf[OAuth2ServerError]) => Success(succeed)
        case _ => Failure(fail())
      }
      .futureValue
  }
}
