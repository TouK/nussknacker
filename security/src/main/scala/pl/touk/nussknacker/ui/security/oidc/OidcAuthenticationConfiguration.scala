package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.util.config.FicusReaders.forDecoder
import pl.touk.nussknacker.engine.util.config.URIExtensions
import pl.touk.nussknacker.ui.security.api.{AuthenticationConfiguration, FrontendStrategySettings}
import pl.touk.nussknacker.ui.security.oauth2.{JwtConfiguration, OAuth2Configuration, TokenCookieConfig}
import pl.touk.nussknacker.ui.security.oauth2.ProfileFormat.OIDC
import pl.touk.nussknacker.ui.security.oauth2.UsernameClaim.UsernameClaim
import sttp.client3.SttpBackend
import sttp.model.MediaType

import java.net.URI
import java.security.PublicKey
import scala.concurrent.{ExecutionContext, Future}

final case class OidcAuthenticationConfiguration(
    usersFile: URI,
    anonymousUserRole: Option[String] = None,
    issuer: URI,
    clientId: String,
    clientSecret: Option[String],
    redirectUri: Option[URI] = None,
    audience: Option[String] = None,
    scope: String = "openid profile",

    // The following values are used for overriding the ones obtained
    // from the OIDC Discovery or in case it is not supported at all.
    // They may be relative to the issuer.
    authorizationEndpoint: Option[URI] = None,
    tokenEndpoint: Option[URI] = None,
    userinfoEndpoint: Option[URI] = None,
    jwksUri: Option[URI] = None,
    rolesClaims: Option[List[String]] = None,
    tokenCookie: Option[TokenCookieConfig] = None,
    usernameClaim: Option[UsernameClaim] = None,
    overrideFrontendAuthenticationStrategy: Option[FrontendStrategySettings] = None,
) extends URIExtensions {

  lazy val oAuth2Configuration: OAuth2Configuration = OAuth2Configuration(
    usersFile = usersFile,
    authorizeUri = authorizationEndpoint
      .map(resolveAgainstIssuer)
      .getOrElse(
        throw new NoSuchElementException("An authorizationEndpoint must provided or OIDC Discovery available")
      ),
    clientSecret = clientSecret
      .getOrElse(throw new NoSuchElementException("PKCE not yet supported, provide a client secret")),
    clientId = clientId,
    profileUri = userinfoEndpoint
      .map(resolveAgainstIssuer)
      .getOrElse(throw new NoSuchElementException("An userinfoEndpoint must provided or OIDC Discovery available")),
    profileFormat = Some(OIDC),
    accessTokenUri = tokenEndpoint
      .map(resolveAgainstIssuer)
      .getOrElse(throw new NoSuchElementException("A tokenEndpoint must provided or OIDC Discovery available")),
    redirectUri = redirectUri,
    jwt = Some(new JwtConfiguration {
      override def accessTokenIsJwt: Boolean                 = OidcAuthenticationConfiguration.this.audience.isDefined
      override def userinfoFromIdToken: Boolean              = true
      override def audience: Option[String]                  = OidcAuthenticationConfiguration.this.audience
      override def authServerPublicKey: Option[PublicKey]    = None
      override def idTokenNonceVerificationRequired: Boolean = false
    }),
    authorizeParams = Map("response_type" -> "code", "scope" -> scope) ++
      // To make possible some OIDC compliant servers authorize user to correct API ("resource server"), audience need to be passed.
      // E.g. for Auth0: https://auth0.com/docs/get-started/applications/confidential-and-public-applications/user-consent-and-third-party-applications
      OidcAuthenticationConfiguration.this.audience.map("audience" -> _),
    accessTokenParams = Map("grant_type" -> "authorization_code"),
    accessTokenRequestContentType = MediaType.ApplicationXWwwFormUrlencoded.toString(),
    anonymousUserRole = anonymousUserRole,
    tokenCookie = tokenCookie,
    overrideFrontendAuthenticationStrategy = overrideFrontendAuthenticationStrategy,
    usernameClaim = usernameClaim
  )

  def withDiscovery(
      implicit ec: ExecutionContext,
      sttpBackend: SttpBackend[Future, Any]
  ): OidcAuthenticationConfiguration = {
    val discoveredConfiguration = OidcDiscovery(issuer)
    copy(
      authorizationEndpoint = authorizationEndpoint.orElse(discoveredConfiguration.map(_.authorizationEndpoint)),
      tokenEndpoint = tokenEndpoint.orElse(discoveredConfiguration.map(_.tokenEndpoint)),
      userinfoEndpoint = userinfoEndpoint.orElse(discoveredConfiguration.map(_.userinfoEndpoint)),
      jwksUri = jwksUri.orElse(discoveredConfiguration.map(_.jwksUri))
    )
  }

  def resolvedJwksUri: URI = jwksUri
    .map(resolveAgainstIssuer)
    .getOrElse(throw new NoSuchElementException("A jwksUri must provided or OIDC Discovery available"))

  private def resolveAgainstIssuer(uri: URI): URI = issuer.withTrailingSlash.resolve(uri)
}

object OidcAuthenticationConfiguration {

  def create(config: Config): OidcAuthenticationConfiguration = {
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    import net.ceedubs.ficus.readers.EnumerationReader._
    import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
    implicit val valueReader: ValueReader[FrontendStrategySettings] = forDecoder
    config.as[OidcAuthenticationConfiguration](AuthenticationConfiguration.authenticationConfigPath)
  }

  def createWithDiscovery(
      config: Config
  )(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): OidcAuthenticationConfiguration =
    create(config).withDiscovery

}
