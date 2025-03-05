package pl.touk.nussknacker.ui.security.oidc

import com.auth0.jwk.{JwkProvider, JwkProviderBuilder}
import io.circe.Decoder
import pdi.jwt.JwtAlgorithm
import pl.touk.nussknacker.ui.security.oauth2.{IntrospectedAccessTokenData, OAuth2ClientApi}
import pl.touk.nussknacker.ui.security.oauth2.jwt.JwtValidator
import pl.touk.nussknacker.ui.security.oidc.OidcService.createJwtValidator
import sttp.client3.SttpBackend

import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.{ExecutionContext, Future}

class OidcService(configuration: OidcAuthenticationConfiguration)(
    implicit ec: ExecutionContext,
    sttpBackend: SttpBackend[Future, Any]
) extends GenericOidcService[OidcUserInfo, DefaultOidcAuthorizationData](
      OAuth2ClientApi[OidcUserInfo, DefaultOidcAuthorizationData](configuration.oAuth2Configuration)(
        OidcUserInfo.decoderWithCustomRolesClaim(configuration.rolesClaims),
        implicitly[Decoder[DefaultOidcAuthorizationData]],
        ec,
        sttpBackend
      ),
      configuration.oAuth2Configuration
    )(OidcUserInfo.decoderWithCustomRolesClaim(configuration.rolesClaims), ec) {

  override protected def toIntrospectedData(claims: OidcUserInfo): IntrospectedAccessTokenData = {
    super.toIntrospectedData(claims).copy(roles = claims.roles)
  }

  override protected lazy val jwtValidator: JwtValidator = createJwtValidator(configuration)

}

object OidcService {

  private[oidc] def createJwtValidator(configuration: OidcAuthenticationConfiguration) = new JwtValidator(jwtHeader => {
    jwtHeader.keyId match {
      case Some(definedKeyId) =>
        jwkProvider(configuration).get(definedKeyId).getPublicKey
      case None =>
        val definedClientSecret = configuration.clientSecret.getOrElse(
          throw new IllegalStateException(
            "clientSecret must be specified in configuration for JWT without keyId for OIDC"
          )
        )
        new SecretKeySpec(
          definedClientSecret.getBytes(StandardCharsets.UTF_8),
          jwtHeader.algorithm.getOrElse(JwtAlgorithm.HS256).name
        )
    }
  })

  private def jwkProvider(configuration: OidcAuthenticationConfiguration): JwkProvider = new JwkProviderBuilder(
    configuration.resolvedJwksUri.toURL
  ).build()

}
