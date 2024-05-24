package pl.touk.nussknacker.security

import io.circe.generic.JsonCodec

sealed trait AuthCredentials

object AuthCredentials {

  final case class PassedAuthCredentials(value: String) extends AuthCredentials

  final case class ImpersonatedAuthCredentials(
      impersonatingUserCredentials: PassedAuthCredentials,
      impersonatedUserData: ImpersonatedUserData
  ) extends AuthCredentials

  case object NoCredentialsProvided extends AuthCredentials

}

@JsonCodec final case class ImpersonatedUserData(id: String, username: String, roles: Set[String])
