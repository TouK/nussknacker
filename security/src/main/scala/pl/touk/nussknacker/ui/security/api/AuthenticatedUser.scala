package pl.touk.nussknacker.ui.security.api

sealed trait BaseAuthenticationUserInfo {
  val id: String
  val username: String
  val roles: Set[String]
}

final case class AuthenticatedUser(
    id: String,
    username: String,
    roles: Set[String],
    impersonatedAuthenticationUser: Option[BaseAuthenticationUserInfo] = None
) extends BaseAuthenticationUserInfo

object AuthenticatedUser {
  def createAnonymousUser(roles: Set[String]): AuthenticatedUser =
    AuthenticatedUser("anonymous", "anonymous", roles)
}
