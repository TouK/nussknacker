package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI

import pl.touk.nussknacker.ui.security.{AuthenticationBackend, AuthenticationConfiguration}

case class OAuth2Configuration(backend: AuthenticationBackend.Value,
                               authorizeUri: URI,
                               clientSecret: String,
                               clientId: String,
                               profileUri: URI,
                               accessTokenUri: URI,
                               redirectUri: URI,
                               accessTokenParams: Map[String, String] = Map.empty,
                               authorizeParams: Map[String, String] = Map.empty,
                               headers: Map[String, String] = Map.empty,
                               authorizationHeader: String = "Authorization") extends AuthenticationConfiguration {

  override def getBackend(): AuthenticationBackend.Value = backend

  override def getAuthorizeUri(): Option[URI] = Option.apply({
    new URI(dispatch.url(authorizeUri.toString)
      .setQueryParameters((Map(
        "client_id" -> clientId,
        "redirect_uri" -> redirectUrl
      ) ++ authorizeParams).mapValues(v => Seq(v)))
      .url)
  })

  def redirectUrl: String = redirectUri.toString
}


