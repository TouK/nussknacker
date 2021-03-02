package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait OpenIdConnectAuthorizationData extends OAuth2AuthorizationData {
  val idToken: Option[String]
}

class OpenIdConnectService[
  UserData: Decoder,
  AuthorizationData <: OpenIdConnectAuthorizationData : Decoder,
  JwtClaims <: JwtStandardClaims : Decoder
](clientApi: OAuth2ClientApi[UserData, AuthorizationData],
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext)
  extends JwtOAuth2Service[UserData, AuthorizationData, JwtClaims](
    clientApi, configuration)
    with LazyLogging {

  protected val useIdToken: Boolean = configuration.jwt.exists(_.userinfoFromIdToken)

  override def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(AuthorizationData, Option[UserData])] = {
    clientApi.accessTokenRequest(authorizationCode).flatMap { authorization =>
      val userInfoFromIdToken: Option[UserData] =
        authorization.idToken.filter(_ => useIdToken).flatMap(introspectToken[UserData])
      userInfoFromIdToken.map(Future(_))
        .getOrElse(clientApi.profileRequest(authorization.accessToken))
        .map(userInfo => (authorization, Some(userInfo)))
    }
  }
}

@ConfiguredJsonCodec case class DefaultOpenIdConnectAuthorizationData
(
  @JsonKey("access_token") accessToken: String,
  @JsonKey("token_type") tokenType: String,
  @JsonKey("refresh_token") refreshToken: Option[String],
  @JsonKey("expires_in") expirationPeriod: Option[FiniteDuration] = None,
  @JsonKey("id_token") idToken: Option[String] = None
) extends OpenIdConnectAuthorizationData

object DefaultOpenIdConnectAuthorizationData extends EpochSecondsCodecs {
  implicit val config: Configuration = Configuration.default
}

object OpenIdConnectService {
  def apply(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): OpenIdConnectService[OpenIdConnectUserInfo, DefaultOpenIdConnectAuthorizationData, DefaultJwtAccessToken] =
    new OpenIdConnectService(OAuth2ClientApi[OpenIdConnectUserInfo, DefaultOpenIdConnectAuthorizationData](configuration), configuration)
}