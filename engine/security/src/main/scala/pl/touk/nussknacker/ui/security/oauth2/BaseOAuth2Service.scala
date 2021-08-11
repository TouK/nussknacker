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

  def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(AuthorizationData, Option[UserInfoData])] = {
    clientApi.accessTokenRequest(authorizationCode).flatMap { authorizationData =>
      clientApi.profileRequest(authorizationData.accessToken).map(Some(_)).map((authorizationData, _))
    }
  }

  final def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])] =
    for {
      deadline <- introspectAccessToken(accessToken)
      userInfo <- obtainUserInfo(accessToken)
    } yield (userInfo, deadline)

  protected def introspectAccessToken(accessToken: String): Future[Option[Deadline]] =
    Future.failed(OAuth2CompoundException(one(OAuth2AccessTokenRejection("The access token cannot be validated"))))

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