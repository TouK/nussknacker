package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.SecurityDirectives
import pl.touk.nussknacker.ui.security.api.AuthenticationResources.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, DefaultAuthenticationConfiguration}

class BasicAuthenticationResources(realm: String, configuration: DefaultAuthenticationConfiguration, allCategories: List[String]) extends AuthenticationResources {
  val name: String = configuration.method.toString

  def authenticate(): LoggedUserAuth =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(configuration, allCategories),
      realm = realm
    )
}
