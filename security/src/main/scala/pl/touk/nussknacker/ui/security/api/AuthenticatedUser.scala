package pl.touk.nussknacker.ui.security.api

final case class AuthenticatedUser(id: String, username: String, roles: Set[String])

final case class AuthCredentials(value: String) extends AnyVal
