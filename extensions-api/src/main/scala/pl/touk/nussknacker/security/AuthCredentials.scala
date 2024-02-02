package pl.touk.nussknacker.security

import java.util.Base64

sealed trait AuthCredentials
object AuthCredentials {
  final case class PassedAuthCredentials(value: String) extends AuthCredentials
  case object AnonymousAccess extends AuthCredentials

  def fromString(value: String): AuthCredentials = {
    val creds = new String(Base64.getDecoder.decode(value))
    if (creds == "anonymous:anonymous") {
      AnonymousAccess
    } else {
      PassedAuthCredentials(value)
    }
  }

  def asPassedAuthCredentials(value: String): AuthCredentials = PassedAuthCredentials(value)

  implicit class Stringify(authCredentials: AuthCredentials) {
    def stringify: String = authCredentials match {
      case PassedAuthCredentials(value) => value
      case AnonymousAccess => new String(Base64.getEncoder.encode("anonymous:anonymous".getBytes))
    }
  }
}

