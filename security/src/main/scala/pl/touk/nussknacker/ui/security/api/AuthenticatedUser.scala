package pl.touk.nussknacker.ui.security.api

private[api] sealed trait UserBasicInfo {
  val id: String
  val username: String
  val roles: Set[String]
}

final case class ImpersonatedUser(id: String, username: String, roles: Set[String]) extends UserBasicInfo

final case class AuthenticatedUser(
    id: String,
    username: String,
    roles: Set[String],
    impersonatedUser: Option[ImpersonatedUser] = None
) extends UserBasicInfo

object AuthenticatedUser {
  def createAnonymousUser(roles: Set[String]): AuthenticatedUser =
    AuthenticatedUser("anonymous", "anonymous", roles)
}
