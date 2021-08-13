package pl.touk.nussknacker.ui.security.oauth2

import cats.data.NonEmptyList.one
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2AccessTokenRejection, OAuth2CompoundException}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

class BaseOAuth2Service[
  UserInfoData: Decoder,
  AuthorizationData <: OAuth2AuthorizationData : Decoder
](protected val clientApi: OAuth2ClientApi[UserInfoData, AuthorizationData])
 (implicit ec: ExecutionContext) extends OAuth2Service[UserInfoData, AuthorizationData] with LazyLogging {

  final def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(AuthorizationData, UserInfoData)] = {
    for {
      authorizationData <- obtainAuthorization(authorizationCode, redirectUri)
      userInfo <- obtainUserInfo(authorizationData)
    } yield (authorizationData, userInfo)
  }

  final def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])] =
    for {
      deadline <- introspectAccessToken(accessToken)
      userInfo <- obtainUserInfo(accessToken)
    } yield (userInfo, deadline)

  protected def obtainAuthorization(authorizationCode: String, redirectUri: String): Future[AuthorizationData] =
    clientApi.accessTokenRequest(authorizationCode, redirectUri)

  /*
  Override this method in a subclass making use of signed tokens or an introspection endpoint
  or use a CachingOAuthService wrapper so that only previously-stored (immediately after retrieval) tokens are accepted
  or do both.
  */
  protected def introspectAccessToken(accessToken: String): Future[Option[Deadline]] = {
    Future.failed(OAuth2CompoundException(one(OAuth2AccessTokenRejection("The access token cannot be validated"))))
  }

  /*
  OAuth2 itself is not an authentication framework. However, we can treat it so. All we need is a restricted resource
  that provides information about a user only with his valid access token.
  The following two methods shall call such a resource.
   */
  protected def obtainUserInfo(authorizationData: AuthorizationData): Future[UserInfoData] =
    obtainUserInfo(authorizationData.accessToken)

  protected def obtainUserInfo(accessToken: String): Future[UserInfoData] =
    clientApi.profileRequest(accessToken)
}

@ConfiguredJsonCodec case class DefaultOAuth2AuthorizationData
(
  @JsonKey("access_token") accessToken: String,
  @JsonKey("token_type") tokenType: String,
  @JsonKey("refresh_token") refreshToken: Option[String] = None,
  @JsonKey("expires_in") expirationPeriod: Option[FiniteDuration] = None
) extends OAuth2AuthorizationData

object DefaultOAuth2AuthorizationData extends EpochSecondsCodecs {
  implicit val config: Configuration = Configuration.default
}

object BaseOAuth2Service {
  def apply[
    UserInfoData: Decoder
  ](configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): BaseOAuth2Service[UserInfoData, DefaultOAuth2AuthorizationData] =
    new BaseOAuth2Service(OAuth2ClientApi[UserInfoData, DefaultOAuth2AuthorizationData](configuration))
}