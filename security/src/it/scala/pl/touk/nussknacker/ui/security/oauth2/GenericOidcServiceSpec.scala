package pl.touk.nussknacker.ui.security.oauth2

import com.dimafeng.testcontainers.{ForAllTestContainer, SingleContainer}
import dasniko.testcontainers.keycloak.KeycloakContainer
import org.apache.commons.text.StringEscapeUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime._
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.ui.security.oidc.{
  DefaultOidcAuthorizationData,
  OidcAuthenticationConfiguration,
  OidcProfileAuthentication,
  OidcService,
  OidcUserInfo
}
import sttp.client3.{SttpBackend, _}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.net.URI
import scala.concurrent.Future

class GenericOidcServiceSpec extends AnyFunSuite with ForAllTestContainer with Matchers with VeryPatientScalaFutures {

  private val realmClientSecret = "123456789"

  private val realmId = "testrealm"

  private val realmClientId = "test-client"

  override val container: KeyCloakScalaContainer = new KeyCloakScalaContainer

  private implicit val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()

  private def baseUrl: String = s"${container.container.getAuthServerUrl}/realms/$realmId"

  test("Basic OpenIDConnect flow") {
    val config = oauth2Conf
    val oidcService = new UserMappingOAuth2Service[OidcUserInfo, DefaultOidcAuthorizationData](
      new OidcService(config),
      new OidcProfileAuthentication(config.oAuth2Configuration)
    )

    val oidcServiceWithCache = new CachingOAuth2Service(oidcService, config.oAuth2Configuration)

    List(oidcService, oidcServiceWithCache).foreach { open =>
      // we emulate FE part
      val loginResult       = keyCloakLogin(config.oAuth2Configuration)
      val authorizationCode = uri"${loginResult.header("Location").get}".params.get("code").get

      val (authData, userData) =
        open.obtainAuthorizationAndAuthenticateUser(authorizationCode, config.redirectUri.get.toString).futureValue
      userData.username shouldBe "user1"
      userData.roles should contain("ARole")

      val profile = open.checkAuthorizationAndAuthenticateUser(authData.accessToken).futureValue
      profile._1.username shouldBe "user1"
      profile._1.roles should contain("ARole")
    }
  }

  // We emulate keycloak login form :)
  private def keyCloakLogin(config: OAuth2Configuration): Response[Either[String, String]] = {
    val redirectValue = basicRequest
      .get(uri"${config.authorizeUrl.get.toString}")
      .response(asString)
      .send(backend)
      .futureValue

    val pattern = """.*action="([^"]*)".*""".r
    val passwordLocation = redirectValue.body.toOption.get.replaceAll("\n", "") match {
      case pattern(url) => StringEscapeUtils.unescapeHtml4(url)
    }

    basicRequest
      .post(uri"$passwordLocation")
      .cookies(redirectValue.unsafeCookies)
      .body("username" -> "user1", "password" -> "pass1", "credentialId" -> "")
      .followRedirects(false)
      .send(backend)
      .futureValue
  }

  private def oauth2Conf: OidcAuthenticationConfiguration =
    OidcAuthenticationConfiguration(
      usersFile = new URI("classpath:users.conf"),
      issuer = uri"$baseUrl".toJavaUri,
      clientId = realmClientId,
      clientSecret = Some(realmClientSecret),
      redirectUri = Some(URI.create("http://localhost:1234")),
      audience = Some("test-client"),
      rolesClaims = Some(List("http://namespace/roles", "http://other.namespace/roles")),
      usernameClaim = Some(UsernameClaim.PreferredUsername),
    ).withDiscovery

}

class KeyCloakScalaContainer() extends SingleContainer[KeycloakContainer] {

  override val container: KeycloakContainer = new KeycloakContainer()
    // sample keycloak realm...
    .withRealmImportFile("/keycloak.json")

}
