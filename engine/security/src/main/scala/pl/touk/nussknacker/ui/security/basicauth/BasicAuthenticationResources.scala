package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, AuthenticationResources}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration) extends AuthenticationResources {
  val name: String = configuration.name

  def authenticate(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(configuration),
      realm = realm
    )
}
