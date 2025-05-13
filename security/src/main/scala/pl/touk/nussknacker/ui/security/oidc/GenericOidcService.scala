package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.oauth2._
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

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

  override protected def authenticateUser(authorization: AuthorizationData): Future[UserData] = {
    if (useIdToken) {
      val idToken = authorization.idToken.get
      introspectJwtToken[UserData](idToken)
        .filter(_.audienceAsList == List(configuration.clientId))
    } else {
      super.authenticateUser(authorization)
    }
  }

}

@ConfiguredJsonCodec final case class DefaultOidcAuthorizationData(
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
  ): GenericOidcService[OidcUserInfo, DefaultOidcAuthorizationData] =
    new GenericOidcService(
      OAuth2ClientApi[OidcUserInfo, DefaultOidcAuthorizationData](configuration),
      configuration
    ) {

      override protected def toIntrospectedData(claims: OidcUserInfo): IntrospectedAccessTokenData = {
        super.toIntrospectedData(claims).copy(roles = claims.roles)
      }

    }

}
