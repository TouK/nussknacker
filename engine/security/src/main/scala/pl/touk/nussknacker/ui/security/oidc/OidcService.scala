package pl.touk.nussknacker.ui.security.oidc

import pl.touk.nussknacker.ui.security.oauth2.{DefaultJwtAccessToken, DefaultOidcAuthorizationData, JwtValidator, OAuth2ClientApi, GenericOidcService, OpenIdConnectUserInfo}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OidcService(configuration: OidcAuthenticationConfiguration)
                 (implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends GenericOidcService[OpenIdConnectUserInfo, DefaultOidcAuthorizationData, DefaultJwtAccessToken](OAuth2ClientApi[OpenIdConnectUserInfo, DefaultOidcAuthorizationData](configuration.oAuth2Configuration), configuration.oAuth2Configuration) {

  override protected lazy val jwtValidator: JwtValidator = new JwtValidator(keyId => configuration.jwkProvider.get(keyId.get).getPublicKey)
}
