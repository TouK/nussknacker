package pl.touk.nussknacker.ui.security.oauth2

import com.dimafeng.testcontainers.{ForAllTestContainer, SingleContainer}
import dasniko.testcontainers.keycloak.KeycloakContainer
import org.apache.commons.lang3.StringEscapeUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.ui.security.oidc.{OidcAuthenticationConfiguration, OidcService}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{SttpBackend, _}

import java.net.URI
import scala.concurrent.Future

class GenericOidcServiceSpec extends FunSuite with ForAllTestContainer with Matchers with VeryPatientScalaFutures {

  private val realmClientSecret = "123456789"

  private val realmId = "testrealm"

  private val realmClientId = "test-client"

  override val container: KeyCloakScalaContainer = new KeyCloakScalaContainer

  private implicit val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()

  private def baseUrl: String = s"${container.container.getAuthServerUrl}/realms/$realmId"


  test("Basic OpenIDConnect flow") {

    val config = oauth2Conf
    // TODO: Change to JWT then token caching will not be required.
    //  However, with the current configuration KeyCloak access tokens seem not to contain the AUD claim.
    val open = new CachingOAuth2Service(
      new UserMappingOAuth2Service(
        new OidcService(config),
        (userInfo: OpenIdConnectUserInfo) => OpenIdConnectProfile.getAuthenticatedUser(userInfo, config.oAuth2Configuration)
      ),
      config.oAuth2Configuration
    )

    //we emulate FE part
    val loginResult = keyCloakLogin(config.oAuth2Configuration)
    val authorizationCode = uri"${loginResult.header("Location").get}".params.get("code").get

    val (authData, userData) = open.obtainAuthorizationAndUserInfo(authorizationCode, config.redirectUri.get.toString).futureValue
    userData.username shouldBe "user1"
    userData.roles should contain ("ARole")

    val profile = open.checkAuthorizationAndObtainUserinfo(authData.accessToken).futureValue
    profile._1.username shouldBe "user1"
    profile._1.roles should contain ("ARole")
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

  private def oauth2Conf: OidcAuthenticationConfiguration =
    OidcAuthenticationConfiguration(
      usersFile = new URI("classpath:users.conf"),
      issuer = uri"$baseUrl".toJavaUri,
      clientId = realmClientId,
      clientSecret = Some(realmClientSecret),
      redirectUri = Some(URI.create("http://localhost:1234")),
      rolesClaim = Some("http://namespace/roles")
    ).withDiscovery
}

class KeyCloakScalaContainer() extends SingleContainer[KeycloakContainer] {
  override val container: KeycloakContainer = new KeycloakContainer()
    //sample keycloak realm...
    .withRealmImportFile("keycloak.json")
}