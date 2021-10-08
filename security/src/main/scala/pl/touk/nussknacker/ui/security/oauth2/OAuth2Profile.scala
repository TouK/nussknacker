package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.AuthenticatedUser

trait OAuth2Profile[ProfileResponse] {
  def getAuthenticatedUser(profile: ProfileResponse, configuration: OAuth2Configuration): AuthenticatedUser
}

object OAuth2Profile {
  def getUserRoles(identity: String, configuration: OAuth2Configuration, defaults: Set[String] = Set.empty): Set[String] =
    configuration
      .users
      .find(us => identity.equals(us.identity))
      .map(_.roles ++ defaults)
      .getOrElse(defaults)
}
