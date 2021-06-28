package pl.touk.nussknacker.ui.security.oauth2

import pl.touk.nussknacker.ui.security.api.LoggedUser

trait OAuth2Profile[ProfileResponse] {
  def getLoggedUser(profile: ProfileResponse, configuration: OAuth2Configuration, allCategories: List[String]): LoggedUser
}

object OAuth2Profile {
  val defaultUserRole = "User"

  def getUserRoles(email: Option[String], configuration: OAuth2Configuration, defaults: List[String] = List(defaultUserRole)): List[String] =
    configuration
      .users
      .find(us => email.exists(_.toLowerCase.equals(us.identity.toLowerCase)))
      .map(_.roles ++ defaults)
      .getOrElse(defaults)
}
