package pl.touk.nussknacker.restmodel

final case class AuthenticatedUser(id: String, username: String, roles: Set[String])

final case class AuthCredentials(value: String) extends AnyVal
