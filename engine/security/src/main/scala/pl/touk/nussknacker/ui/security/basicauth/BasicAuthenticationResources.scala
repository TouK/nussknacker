package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.SecurityDirectives
import pl.touk.nussknacker.ui.security.api.AuthenticationResources.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration, allCategories: List[String]) extends AuthenticationResources {
  val name: String = configuration.name

  def authenticate(): LoggedUserAuth =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(configuration, allCategories),
      realm = realm
    )
}
