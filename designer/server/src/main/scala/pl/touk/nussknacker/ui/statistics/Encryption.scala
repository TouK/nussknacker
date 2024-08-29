package pl.touk.nussknacker.ui.statistics

import com.typesafe.scalalogging.LazyLogging

import java.nio.charset.StandardCharsets
import java.security.spec.{EncodedKeySpec, X509EncodedKeySpec}
import java.security.{Key, KeyFactory, PublicKey}
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import scala.util.{Failure, Success, Try}

object Encryption extends LazyLogging {
  private lazy val privateSymmetricKey: SecretKey = createSymmetricKey

  def encrypt(asymmetricPublicKey: PublicEncryptionKey, toEncode: String): Either[StatisticError, EncryptionResult] = {
    val result = for {
      encryptedBySymmetricKey <- cipherAndEncode(
        toEncode.getBytes(StandardCharsets.UTF_8),
        EncryptionType.AES,
        privateSymmetricKey
      )
      encryptedSymmetricKeyWithPublicRSA <- cipherAndEncode(
        privateSymmetricKey.getEncoded,
        EncryptionType.RSA,
        asymmetricPublicKey.key
      )
    } yield EncryptionResult(encryptedSymmetricKeyWithPublicRSA, encryptedBySymmetricKey)
    result.toEither.left.map { t: Throwable =>
      logger.warn("Encryption failed with exception", t)
      CannotEncryptURLError
    }
  }

  private def cipherAndEncode(toEncode: Array[Byte], encryptionType: EncryptionType.Value, key: Key): Try[String] =
    Try {
      val cipher: Cipher = Cipher.getInstance(encryptionType.encryption)
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val encoded = cipher.doFinal(toEncode)
      Base64.getEncoder.encodeToString(encoded)
    }

  private[statistics] def createSymmetricKey: SecretKey = {
    val keyGenerator: KeyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256)
    keyGenerator.generateKey()
  }

}

final case class EncryptionResult(encryptedSymmetricKey: String, encryptedValue: String)

object EncryptionType extends Enumeration {
  protected case class EncryptionTypeVal(encryption: String) extends super.Val
  import scala.language.implicitConversions
  implicit def valueToEncryptionTypeVal(x: Value): EncryptionTypeVal = x.asInstanceOf[EncryptionTypeVal]

  val RSA: EncryptionTypeVal = EncryptionTypeVal("RSA/ECB/PKCS1Padding")
  val AES: EncryptionTypeVal = EncryptionTypeVal("AES/ECB/PKCS5Padding")
}

final case class PublicEncryptionKey(key: PublicKey)

object PublicEncryptionKey {

  def apply(plainPublicEncryptionKey: String): PublicEncryptionKey =
    setupPublicKey(plainPublicEncryptionKey)
      .transform(
        k => Success(new PublicEncryptionKey(k)),
        ex => Failure(new IllegalStateException("Cannot setup public encryption key", ex))
      )
      .get

  private[statistics] def setupPublicKey(plainKey: String): Try[PublicKey] = {
    Try {
      val publicKeyBytes          = Base64.getDecoder.decode(plainKey)
      val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
      val keySpec: EncodedKeySpec = new X509EncodedKeySpec(publicKeyBytes)
      keyFactory.generatePublic(keySpec)
    }
  }

}
