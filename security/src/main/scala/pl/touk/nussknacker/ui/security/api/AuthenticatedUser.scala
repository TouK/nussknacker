package pl.touk.nussknacker.ui.security.api

final case class AuthenticatedUser(
    id: String,
    username: String,
    roles: Set[String],
    impersonatedAuthenticationUser: Option[AuthenticatedUser] = None
)

object AuthenticatedUser {
  def createAnonymousUser(roles: Set[String]): AuthenticatedUser =
    AuthenticatedUser("anonymous", "anonymous", roles)
}
