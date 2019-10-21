package pl.touk.nussknacker.ui.api

import java.net.URI

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withoutPermissions
import pl.touk.nussknacker.ui.security.AuthenticationBackend
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthConfiguration
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration

import scala.language.higherKinds

class AuthenticationResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with ScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  val authenticationResourcesBasicAuth = new AuthenticationResources(BasicAuthConfiguration(
    AuthenticationBackend.BasicAuth
  ))

  val authenticationResources = new AuthenticationResources(OAuth2Configuration(
    AuthenticationBackend.OAuth2,
    URI.create("http://demo.nussknacker.pl/oauth/authorize"),
    "clientSecret",
    "clientId",
    URI.create("http://demo.nussknacker.pl/api/user"),
    URI.create("http://demo.nussknacker.pl/oauth/token"),
    URI.create("http://demo.nussknacker.pl/api/authentication/oauth2")
  ))

  val authenticationRoutesWithoutPermissions = withoutPermissions(authenticationResources)

  val authenticationBasicAuthRoutesWithoutPermissions = withoutPermissions(authenticationResourcesBasicAuth)

  def authenticationOauth2(authorizeToken: String) = {
    Get(s"/authentication/oauth2?code=$authorizeToken") ~> authenticationBasicAuthRoutesWithoutPermissions
  }

  it("should return 400 for BasicAuth backend at /authentication/oauth2") {
    authenticationOauth2("testToken") ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }
}