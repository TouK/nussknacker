package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.security.ImpersonatedUserData

final case class AuthenticatedUser(
    id: String,
    username: String,
    roles: Set[String],
    impersonatedAuthenticationUser: Option[AuthenticatedUser] = None
)

object AuthenticatedUser {
  def createAnonymousUser(roles: Set[String]): AuthenticatedUser =
    AuthenticatedUser("anonymous", "anonymous", roles)

  def createImpersonatedUser(
      impersonatingUser: AuthenticatedUser,
      impersonatedUserData: ImpersonatedUserData
  ): AuthenticatedUser =
    AuthenticatedUser(
      impersonatingUser.id,
      impersonatingUser.username,
      impersonatingUser.roles,
      Some(AuthenticatedUser(impersonatedUserData))
    )

  def apply(userData: ImpersonatedUserData): AuthenticatedUser =
    AuthenticatedUser(userData.id, userData.username, userData.roles)
}
