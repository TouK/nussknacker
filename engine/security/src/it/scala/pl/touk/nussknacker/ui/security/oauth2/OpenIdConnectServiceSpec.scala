package pl.touk.nussknacker.ui.security.oauth2

import com.dimafeng.testcontainers.{ForAllTestContainer, SingleContainer}
import dasniko.testcontainers.keycloak.KeycloakContainer
import org.apache.commons.lang3.StringEscapeUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{SttpBackend, _}
import sttp.model.MediaType

import java.net.URI
import scala.concurrent.Future

class OpenIdConnectServiceSpec extends FunSuite with ForAllTestContainer with Matchers with VeryPatientScalaFutures {

  private val realmClientSecret = "123456789"

  private val realmId = "testrealm"

  private val realmClientId = "test-client"

  override val container: KeyCloakScalaContainer = new KeyCloakScalaContainer

  private implicit val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()

  private def baseUrl: String = s"${container.container.getAuthServerUrl}/realms/$realmId/protocol/openid-connect"


  test("Basic OpenIDConnect flow") {

    val config = oauth2Conf
    val open = OpenIdConnectService(config)

    //we emulate FE part
    val loginResult = keyCloakLogin(config)
    val authorizationCode = uri"${loginResult.header("Location").get}".params.get("code").get

    val (authData, userData) = open.obtainAuthorizationAndUserInfo(authorizationCode).futureValue
    userData.get.name shouldBe Some("Jan Kowalski")

    val profile = open.checkAuthorizationAndObtainUserinfo(authData.accessToken).futureValue
    profile._1.name shouldBe Some("Jan Kowalski")
  }

  //We emulate keycloak login form :)
  private def keyCloakLogin(config: OAuth2Configuration): Response[Either[String, String]] = {
    val redirectValue = basicRequest
      .get(uri"${config.authorizeUrl.get.toString}")
      .response(asString)
      .send().futureValue

    val pattern = """.*action="([^"]*)".*""".r
    val passwordLocation = redirectValue.body.right.get.replaceAll("\n", "") match {
      case pattern(url) => StringEscapeUtils.unescapeHtml4(url)
    }

    basicRequest
      .post(uri"$passwordLocation")
      .cookies(redirectValue.cookies)
      .body("username" -> "user1", "password" -> "pass1", "credentialId" -> "")
      .followRedirects(false)
      .send().futureValue
  }

  private def oauth2Conf: OAuth2Configuration = {

    OAuth2Configuration(
      usersFile = new URI("classpath://users.conf"),
      authorizeUri = uri"$baseUrl/auth".toJavaUri,
      clientSecret = realmClientSecret,
      clientId = realmClientId,
      profileUri = uri"$baseUrl/userinfo".toJavaUri,
      profileFormat = None,
      accessTokenUri = uri"$baseUrl/token".toJavaUri,
      redirectUri = new URI("http://localhost:1234"),
      implicitGrantEnabled = false,
      jwt = None,
      accessTokenRequestContentType = MediaType.ApplicationXWwwFormUrlencoded.toString(),
      accessTokenParams = Map(
        "grant_type" -> "authorization_code"
      ), authorizeParams = Map(
        "scope" -> "openid profile email",
        "response_type" -> "code"
      )
    )
  }

}

class KeyCloakScalaContainer() extends SingleContainer[KeycloakContainer] {
  override val container: KeycloakContainer = new KeycloakContainer()
    //sample keycloak realm...
    .withRealmImportFile("keycloak.json")
}