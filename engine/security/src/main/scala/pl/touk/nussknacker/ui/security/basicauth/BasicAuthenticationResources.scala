package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.ui.security.api.{AnonymousAccess, AuthenticatedUser, AuthenticationResources, FrontendStrategySettings}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration) extends AuthenticationResources with AnonymousAccess {
  val name: String = configuration.name

  val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  val anonymousUserRole: Option[String] = configuration.anonymousUserRole

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = BasicHttpAuthenticator(configuration),
      realm = realm
    )

}