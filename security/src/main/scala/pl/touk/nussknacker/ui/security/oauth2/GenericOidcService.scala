package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import sttp.client3.SttpBackend

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait OidcAuthorizationData extends OAuth2AuthorizationData {
  val idToken: Option[String]
}

/** Apart from backward compatibility of the configuration, this class is not intended for direct instantiation.
  * There is a concrete subclass [[pl.touk.nussknacker.ui.security.oidc.OidcService]] in the Oidc authentication
  * provider for OIDC compliant authorization servers.
  */
class GenericOidcService[
    UserData <: JwtStandardClaims: Decoder,
    AuthorizationData <: OidcAuthorizationData
](clientApi: OAuth2ClientApi[UserData, AuthorizationData], configuration: OAuth2Configuration)(
    implicit ec: ExecutionContext
) extends JwtOAuth2Service[UserData, AuthorizationData, UserData](clientApi, configuration)
    with LazyLogging {

  protected val useIdToken: Boolean = configuration.jwt.exists(_.userinfoFromIdToken)

  override protected def obtainUserInfo(authorization: AuthorizationData): Future[UserData] = {
    if (useIdToken) {
      val idToken = authorization.idToken.get
      introspectJwtToken[UserData](idToken)
        .filter(_.audienceAsList == List(configuration.clientId))
    } else {
      super.obtainUserInfo(authorization)
    }
  }

}

@ConfiguredJsonCodec case class DefaultOidcAuthorizationData(
    @JsonKey("access_token") accessToken: String,
    @JsonKey("token_type") tokenType: String,
    @JsonKey("refresh_token") refreshToken: Option[String] = None,
    @JsonKey("expires_in") expirationPeriod: Option[FiniteDuration] = None,
    @JsonKey("id_token") idToken: Option[String] = None
) extends OidcAuthorizationData

object DefaultOidcAuthorizationData extends RelativeSecondsCodecs {
  implicit val config: Configuration = Configuration.default
}

object GenericOidcService {
  def apply(configuration: OAuth2Configuration)(
      implicit ec: ExecutionContext,
      backend: SttpBackend[Future, Any]
  ): GenericOidcService[OpenIdConnectUserInfo, DefaultOidcAuthorizationData] =
    new GenericOidcService(
      OAuth2ClientApi[OpenIdConnectUserInfo, DefaultOidcAuthorizationData](configuration),
      configuration
    ) {
      override protected def toIntrospectionResult(claims: OpenIdConnectUserInfo): AccessTokenData = {
        super.toIntrospectionResult(claims).copy(roles = claims.roles)
      }
    }
}
