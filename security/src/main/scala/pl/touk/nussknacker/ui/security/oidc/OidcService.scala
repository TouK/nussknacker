package pl.touk.nussknacker.ui.security.oidc

import com.auth0.jwk.{JwkProvider, JwkProviderBuilder}
import io.circe.Decoder
import pl.touk.nussknacker.ui.security.oauth2.{DefaultJwtAccessToken, DefaultOidcAuthorizationData, GenericOidcService, JwtValidator, OAuth2ClientApi, OpenIdConnectUserInfo}
import pl.touk.nussknacker.ui.security.oidc.OidcService.createJwtValidator
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OidcService(configuration: OidcAuthenticationConfiguration)
                 (implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends {
    implicit private val decoder: Decoder[OpenIdConnectUserInfo] = OpenIdConnectUserInfo.decoderWithCustomRolesClaim(configuration.rolesClaims)
  } with GenericOidcService[OpenIdConnectUserInfo, DefaultOidcAuthorizationData, DefaultJwtAccessToken](OAuth2ClientApi[OpenIdConnectUserInfo, DefaultOidcAuthorizationData](configuration.oAuth2Configuration), configuration.oAuth2Configuration) {

  override protected lazy val jwtValidator: JwtValidator = createJwtValidator(configuration)

}

object OidcService {

  private[oidc] def createJwtValidator(configuration: OidcAuthenticationConfiguration) =  new JwtValidator(keyIdOpt => {
    val keyId = keyIdOpt.getOrElse(throw new IllegalArgumentException("Key must be provided in JWT for OIDC"))
    jwkProvider(configuration).get(keyId).getPublicKey
  })

  private def jwkProvider(configuration: OidcAuthenticationConfiguration): JwkProvider = new JwkProviderBuilder(
    configuration.resolvedJwksUri.toURL
  ).build()

}