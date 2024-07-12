package pl.touk.nussknacker.security

sealed trait AuthCredentials

object AuthCredentials {

  final case class PassedAuthCredentials(value: String) extends AuthCredentials

  final case class ImpersonatedAuthCredentials(
      impersonatingUserCredentials: PassedAuthCredentials,
      impersonatedUserIdentity: ImpersonatedUserIdentity
  ) extends AuthCredentials

  case object NoCredentialsProvided extends AuthCredentials

}

final case class ImpersonatedUserIdentity(value: String)
