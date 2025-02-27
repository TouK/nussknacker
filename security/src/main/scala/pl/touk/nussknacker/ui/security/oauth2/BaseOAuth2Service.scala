package pl.touk.nussknacker.ui.security.oauth2

import cats.data.NonEmptyList.one
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2AccessTokenRejection, OAuth2CompoundException}
import sttp.client3.SttpBackend

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class BaseOAuth2Service[
    UserInfoData,
    AuthorizationData <: OAuth2AuthorizationData
](protected val clientApi: OAuth2ClientApi[UserInfoData, AuthorizationData])(implicit ec: ExecutionContext)
    extends OAuth2Service[UserInfoData, AuthorizationData]
    with LazyLogging {

  final def obtainAuthorizationAndAuthenticateUser(
      authorizationCode: String,
      redirectUri: String
  ): Future[(AuthorizationData, UserInfoData)] = {
    for {
      authorizationData <- obtainAuthorization(authorizationCode, redirectUri)
      userInfo          <- authenticateUser(authorizationData)
    } yield (authorizationData, userInfo)
  }

  protected def obtainAuthorization(authorizationCode: String, redirectUri: String): Future[AuthorizationData] =
    clientApi.accessTokenRequest(authorizationCode, redirectUri)

  /*
  Override this method in a subclass making use of signed tokens or an introspection endpoint
  or use a CachingOAuthService wrapper so that only previously-stored (immediately after retrieval) tokens are accepted
  or do both.
   */
  override private[oauth2] def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData] = {
    Future.failed(OAuth2CompoundException(one(OAuth2AccessTokenRejection("The access token cannot be validated"))))
  }

  /*
  OAuth2 itself is not an authentication framework. However, we can treat it so. All we need is a restricted resource
  that provides information about a user only with his valid access token.
  The following two methods shall call such a resource.
   */
  protected def authenticateUser(authorizationData: AuthorizationData): Future[UserInfoData] =
    authenticateUser(authorizationData.accessToken, IntrospectedAccessTokenData.empty)

  override private[oauth2] def authenticateUser(
      accessToken: String,
      accessTokenData: IntrospectedAccessTokenData
  ): Future[UserInfoData] =
    clientApi.profileRequest(accessToken)

}

@ConfiguredJsonCodec final case class DefaultOAuth2AuthorizationData(
    @JsonKey("access_token") accessToken: String,
    @JsonKey("token_type") tokenType: String,
    @JsonKey("refresh_token") refreshToken: Option[String] = None,
    @JsonKey("expires_in") expirationPeriod: Option[FiniteDuration] = None
) extends OAuth2AuthorizationData

object DefaultOAuth2AuthorizationData extends RelativeSecondsCodecs {
  implicit val config: Configuration = Configuration.default
}

object BaseOAuth2Service {

  def apply[
      UserInfoData: Decoder
  ](configuration: OAuth2Configuration)(
      implicit ec: ExecutionContext,
      backend: SttpBackend[Future, Any]
  ): BaseOAuth2Service[UserInfoData, DefaultOAuth2AuthorizationData] =
    new BaseOAuth2Service(OAuth2ClientApi[UserInfoData, DefaultOAuth2AuthorizationData](configuration))

}
