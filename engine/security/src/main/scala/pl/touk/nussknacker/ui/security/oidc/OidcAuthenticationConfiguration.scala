package pl.touk.nussknacker.ui.security.oidc

import com.auth0.jwk.{JwkProvider, JwkProviderBuilder}
import pl.touk.nussknacker.engine.util.config.URIExtensions
import pl.touk.nussknacker.ui.security.oauth2.{JwtConfiguration, OAuth2Configuration}
import pl.touk.nussknacker.ui.security.oauth2.ProfileFormat.OIDC
import sttp.client.{NothingT, SttpBackend}
import sttp.model.MediaType

import java.net.URI
import java.security.PublicKey
import java.util.NoSuchElementException
import scala.concurrent.{ExecutionContext, Future}

case class OidcAuthenticationConfiguration(usersFile: URI,
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

                                           rolesClaim: Option[String] = None,
                                          ) extends URIExtensions {

  lazy val oAuth2Configuration: OAuth2Configuration = OAuth2Configuration(
    usersFile = usersFile,
    authorizeUri = authorizationEndpoint.map(resolveAgainstIssuer)
      .getOrElse(throw new NoSuchElementException("An authorizationEndpoint must provided or OIDC Discovery available")),
    clientSecret = clientSecret
      .getOrElse(throw new NoSuchElementException("PKCE not yet supported, provide a client secret")),
    clientId = clientId,
    profileUri = userinfoEndpoint.map(resolveAgainstIssuer)
      .getOrElse(throw new NoSuchElementException("An userinfoEndpoint must provided or OIDC Discovery available")),
    profileFormat = Some(OIDC),
    accessTokenUri = tokenEndpoint.map(resolveAgainstIssuer)
      .getOrElse(throw new NoSuchElementException("A tokenEndpoint must provided or OIDC Discovery available")),
    redirectUri = redirectUri,
    jwt = Some(new JwtConfiguration {
      def accessTokenIsJwt: Boolean = OidcAuthenticationConfiguration.this.audience.isDefined
      def userinfoFromIdToken: Boolean = true
      def audience: Option[String] = OidcAuthenticationConfiguration.this.audience
      def authServerPublicKey: Option[PublicKey] = None
      def idTokenNonceVerificationRequired: Boolean = false
    }),
    authorizeParams = Map("response_type" -> "code", "scope" -> scope),
    accessTokenParams = Map("grant_type" -> "authorization_code"),
    accessTokenRequestContentType = MediaType.ApplicationXWwwFormUrlencoded.toString(),
    anonymousUserRole = anonymousUserRole
  )

  lazy val jwkProvider: JwkProvider = new JwkProviderBuilder(
    jwksUri.map(resolveAgainstIssuer)
      .getOrElse(throw new NoSuchElementException("A jwksUri must provided or OIDC Discovery available"))
      .toURL
  ).build()

  def withDiscovery(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OidcAuthenticationConfiguration = {
    val discoveredConfiguration = OidcDiscovery(issuer)
    copy(
      authorizationEndpoint = authorizationEndpoint.orElse(discoveredConfiguration.map(_.authorizationEndpoint)),
      tokenEndpoint = tokenEndpoint.orElse(discoveredConfiguration.map(_.tokenEndpoint)),
      userinfoEndpoint = userinfoEndpoint.orElse(discoveredConfiguration.map(_.userinfoEndpoint)),
      jwksUri = jwksUri.orElse(discoveredConfiguration.map(_.jwksUri))
    )
  }

  private def resolveAgainstIssuer(uri: URI): URI  = issuer.withTrailingSlash.resolve(uri)
}
