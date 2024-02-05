package pl.touk.nussknacker.security

import java.util.Base64
import scala.util.{Failure, Success, Try}

sealed trait AuthCredentials

object AuthCredentials {
  private val anonymousPass = "anonymous"

  final case class PassedAuthCredentials(value: String) extends AuthCredentials
  case object AnonymousAccess                           extends AuthCredentials

  def fromStringToPassedAuthCredentials(value: String): AuthCredentials = {
    PassedAuthCredentials(value)
  }

  def fromStringToAuthCredentials(value: String, crypter: Crypter): AuthCredentials = {
    new String(Base64.getDecoder.decode(value)).split(":").toList match {
      case "anonymous" :: pass :: Nil =>
        crypter.decrypt(pass) match {
          case Failure(_) =>
            PassedAuthCredentials(value)
          case Success(decryptedAnonymousPassword) =>
            decryptedAnonymousPassword match {
              case `anonymousPass` => AnonymousAccess
              case _               => PassedAuthCredentials(value)
            }
        }
      case _ =>
        PassedAuthCredentials(value)
    }
  }

  implicit class Stringify(authCredentials: AuthCredentials) {

    def stringify(crypter: Crypter): String = authCredentials match {
      case PassedAuthCredentials(value) => value
      case AnonymousAccess =>
        new String(Base64.getEncoder.encode(s"anonymous:${crypter.encrypt(anonymousPass)}".getBytes))
    }

  }

}

trait Crypter {
  def encrypt(value: String): String
  def decrypt(value: String): Try[String]
}

object Base64Crypter extends Crypter {
  override def encrypt(value: String): String      = new String(Base64.getEncoder.encode(value.getBytes))
  override def decrypt(value: String): Try[String] = Try(new String(Base64.getDecoder.decode(value)))
}

object NoOpCrypter extends Crypter {
  override def encrypt(value: String): String      = value
  override def decrypt(value: String): Try[String] = Success(value)
}
