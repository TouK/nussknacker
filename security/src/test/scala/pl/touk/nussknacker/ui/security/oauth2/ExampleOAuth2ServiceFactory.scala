package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationConfiguration}
import pl.touk.nussknacker.ui.security.oauth2.ExampleOAuth2ServiceFactory.{TestAccessTokenResponse, TestProfileResponse}
import sttp.client3.SttpBackend

import java.net.URI
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class ExampleOAuth2Service(clientApi: OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse])(implicit ec: ExecutionContext)
  extends OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData] with LazyLogging {

  def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(OAuth2AuthorizationData, AuthenticatedUser)] =
    for {
      accessTokenResponse <- clientApi.accessTokenRequest(authorizationCode, redirectUri)
      authenticatedUser <- checkAuthorizationAndObtainUserinfo(accessTokenResponse.accessToken).map(_._1)
    } yield (accessTokenResponse, authenticatedUser)


  override def introspectAccessToken(accessToken: String): Future[AccessTokenIntrospectionResult] =
    Future.successful(AccessTokenIntrospectionResult(None, None))

  override def obtainUserInfo(accessToken: String, accessTokenSubject: Option[String]): Future[AuthenticatedUser] =
    clientApi.profileRequest(accessToken).map { prf: TestProfileResponse =>
      AuthenticatedUser(
        prf.uid,
        username = prf.email,
        prf.clearance.roles
      )
    }

}

class ExampleOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def create(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): ExampleOAuth2Service =
    ExampleOAuth2ServiceFactory.service(configuration)
}

object ExampleOAuth2ServiceFactory {

  def apply(): ExampleOAuth2ServiceFactory = new ExampleOAuth2ServiceFactory()

  def service(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Any]): ExampleOAuth2Service =
    new ExampleOAuth2Service(testClient(configuration))

  def testClient(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Any]): OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse]
    = new OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse](configuration)

  def testConfig: OAuth2Configuration =
    OAuth2Configuration(
      usersFile = URI.create("classpath:oauth2-users.conf"),
      authorizeUri = URI.create("https://github.com/login/oauth/authorize"),
      clientSecret = "clientSecret",
      clientId = "clientId",
      profileUri = URI.create("https://api.github.com/user"),
      profileFormat = Some(ProfileFormat.GITHUB),
      accessTokenUri = URI.create("https://github.com/login/oauth/access_token"),
      redirectUri = None,
      implicitGrantEnabled = false,
      jwt = None
    )

  val testRules: List[AuthenticationConfiguration.ConfigRule] = AuthenticationConfiguration.getRules(testConfig.usersFile)

  @ConfiguredJsonCodec case class TestAccessTokenResponse(@JsonKey("access_token") accessToken: String,
                                                          @JsonKey("token_type") tokenType: String,
                                                          @JsonKey("expires_in") expirationPeriod: Option[FiniteDuration],
                                                         ) extends OAuth2AuthorizationData {
    val refreshToken: Option[String] = None
  }

  object TestAccessTokenResponse extends RelativeSecondsCodecs {
    implicit val config: Configuration = Configuration.default
  }

  @JsonCodec case class TestProfileResponse(email: String, uid: String, clearance: TestProfileClearanceResponse)
  @JsonCodec case class TestTokenIntrospectionResponse(exp: Option[Long])
  @JsonCodec case class TestProfileClearanceResponse(roles: Set[String])
}
