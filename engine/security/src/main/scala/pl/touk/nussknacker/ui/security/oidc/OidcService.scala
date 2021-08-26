package pl.touk.nussknacker.ui.security.oidc

import io.circe.Decoder
import pl.touk.nussknacker.ui.security.oauth2.{DefaultJwtAccessToken, DefaultOidcAuthorizationData, GenericOidcService, JwtValidator, OAuth2ClientApi, OpenIdConnectUserInfo}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OidcService(configuration: OidcAuthenticationConfiguration)
                 (implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends {
    implicit private val decoder: Decoder[OpenIdConnectUserInfo] = OpenIdConnectUserInfo.decoderWithCustomRolesClaim(configuration.rolesClaim)
  } with GenericOidcService[OpenIdConnectUserInfo, DefaultOidcAuthorizationData, DefaultJwtAccessToken](OAuth2ClientApi[OpenIdConnectUserInfo, DefaultOidcAuthorizationData](configuration.oAuth2Configuration), configuration.oAuth2Configuration) {

  override protected lazy val jwtValidator: JwtValidator = new JwtValidator(keyId => configuration.jwkProvider.get(keyId.get).getPublicKey)
}
