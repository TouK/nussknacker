package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.AuthenticatedUser

trait OAuth2Profile[ProfileResponse] {
  def getAuthenticatedUser(profile: ProfileResponse, configuration: OAuth2Configuration): AuthenticatedUser
}

object OAuth2Profile {
  def getUserRoles(identity: String, configuration: OAuth2Configuration, defaults: Set[String] = Set.empty): Set[String] =
    configuration
      .findUserById(identity)
      .map(_.roles ++ defaults)
      .getOrElse(defaults)

  def getUserUsername(identity: String, configuration: OAuth2Configuration): Option[String] =
    configuration.findUserById(identity).flatMap(_.username)

  private implicit class RichOAuth2Configuration(c: OAuth2Configuration) {
    private[OAuth2Profile] def findUserById(id: String) = c.users.find(_.identity == id)
  }
}
