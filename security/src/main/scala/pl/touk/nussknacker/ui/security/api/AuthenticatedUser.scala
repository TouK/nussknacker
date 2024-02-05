package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.security.AuthCredentials.anonymousUsername

final case class AuthenticatedUser(id: String, username: String, roles: Set[String])

object AuthenticatedUser {
  def createAnonymousUser(roles: Set[String]): AuthenticatedUser =
    AuthenticatedUser(anonymousUsername, anonymousUsername, roles)
}
