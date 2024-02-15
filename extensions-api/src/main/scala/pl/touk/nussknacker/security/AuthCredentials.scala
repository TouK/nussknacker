package pl.touk.nussknacker.security

sealed trait AuthCredentials

object AuthCredentials {

  final case class PassedAuthCredentials(value: String) extends AuthCredentials
  case object AnonymousAccess                           extends AuthCredentials

}
