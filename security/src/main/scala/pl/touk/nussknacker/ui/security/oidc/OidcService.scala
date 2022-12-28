package pl.touk.nussknacker.ui.security.oidc

import com.auth0.jwk.{JwkProvider, JwkProviderBuilder}
import io.circe.Decoder
import pdi.jwt.JwtAlgorithm
import pl.touk.nussknacker.ui.security.oauth2.jwt.JwtValidator
import pl.touk.nussknacker.ui.security.oauth2.{DefaultJwtAccessToken, DefaultOidcAuthorizationData, GenericOidcService, OAuth2ClientApi, OpenIdConnectUserInfo}
import pl.touk.nussknacker.ui.security.oidc.OidcService.createJwtValidator
import sttp.client.{NothingT, SttpBackend}

import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.{ExecutionContext, Future}

class OidcService(configuration: OidcAuthenticationConfiguration)
                 (implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends GenericOidcService[OpenIdConnectUserInfo, DefaultOidcAuthorizationData, DefaultJwtAccessToken](OAuth2ClientApi[OpenIdConnectUserInfo, DefaultOidcAuthorizationData](configuration.oAuth2Configuration), configuration.oAuth2Configuration) {

  //todo kgd
  implicit private val decoder: Decoder[OpenIdConnectUserInfo] = OpenIdConnectUserInfo.decoderWithCustomRolesClaim(configuration.rolesClaims)
  override protected lazy val jwtValidator: JwtValidator = createJwtValidator(configuration)

}

object OidcService {

  private[oidc] def createJwtValidator(configuration: OidcAuthenticationConfiguration) =  new JwtValidator(jwtHeader => {
    jwtHeader.keyId match {
      case Some(definedKeyId) =>
        jwkProvider(configuration).get(definedKeyId).getPublicKey
      case None =>
        val definedClientSecret = configuration.clientSecret.getOrElse(throw new IllegalStateException("clientSecret must be specified in configuration for JWT without keyId for OIDC"))
        new SecretKeySpec(definedClientSecret.getBytes(StandardCharsets.UTF_8), jwtHeader.algorithm.getOrElse(JwtAlgorithm.HS256).name)
    }
  })

  private def jwkProvider(configuration: OidcAuthenticationConfiguration): JwkProvider = new JwkProviderBuilder(
    configuration.resolvedJwksUri.toURL
  ).build()

}

