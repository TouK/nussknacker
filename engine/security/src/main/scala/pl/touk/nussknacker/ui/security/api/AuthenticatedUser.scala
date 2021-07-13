package pl.touk.nussknacker.ui.security.api

case class AuthenticatedUser(id: String, username: String, roles: List[String])
