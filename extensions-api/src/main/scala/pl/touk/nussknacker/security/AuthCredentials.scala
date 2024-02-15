package pl.touk.nussknacker.security

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import scala.util.{Failure, Success, Try}

sealed trait AuthCredentials

object AuthCredentials {

  val anonymousUsername     = "anonymous"
  private val anonymousPass = "anonymous"

  final case class PassedAuthCredentials(value: String) extends AuthCredentials
  case object AnonymousAccess                           extends AuthCredentials

  def fromStringToPassedAuthCredentials(value: String): AuthCredentials = {
    PassedAuthCredentials(value)
  }

  def fromStringToAuthCredentials(value: String, crypter: Crypter): AuthCredentials = {
    new String(Base64.getDecoder.decode(value)).split(":").toList match {
      case `anonymousUsername` :: pass :: Nil =>
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
        new String(Base64.getEncoder.encode(s"$anonymousUsername:${crypter.encrypt(anonymousPass)}".getBytes))
    }

  }

}

sealed trait Crypter {
  def encrypt(value: String): String
  def decrypt(value: String): Try[String]
}

object AesCrypter extends Crypter {

  private val secretKey: SecretKey = KeyGenerator.getInstance("AES").generateKey()

  private val encryptCipher: Cipher = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    cipher
  }

  private val decryptCipher: Cipher = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKey)
    cipher
  }

  override def encrypt(value: String): String = {
    Base64.getEncoder.encodeToString(encryptCipher.doFinal(value.getBytes(UTF_8)))
  }

  override def decrypt(value: String): Try[String] = Try {
    new String(decryptCipher.doFinal(Base64.getDecoder.decode(value)), UTF_8)
  }

}

object NoOpCrypter extends Crypter {
  override def encrypt(value: String): String      = value
  override def decrypt(value: String): Try[String] = Success(value)
}
