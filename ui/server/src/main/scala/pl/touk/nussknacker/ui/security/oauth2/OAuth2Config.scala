package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI

import pl.touk.nussknacker.ui.security.{AuthenticationBackend, AuthenticationConfig}

case class OAuth2Config(backend: AuthenticationBackend.Value,
                        authenticationUrl: URI,
                        clientSecret: String,
                        clientId: String,
                        profileUri: URI,
                        tokenUri: URI,
                        redirectUri: URI,
                        accessTokenParams: Map[String, String],
                        authorizeParams: Map[String, String],
                        headers: Map[String, String]) extends AuthenticationConfig {

  override def getBackend(): AuthenticationBackend.Value = backend

  override def getAuthenticationRedirectUrl(): Option[URI] = Option.apply({
    new URI(dispatch.url(authenticationUrl.toString)
      .setQueryParameters((Map(
        "client_id" -> clientId,
        "redirect_uri" -> redirectUri.toString
      ) ++ authorizeParams).mapValues(v => Seq(v)))
      .url)
  })
}