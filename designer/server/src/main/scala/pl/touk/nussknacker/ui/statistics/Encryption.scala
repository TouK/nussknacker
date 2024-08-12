package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.ui.statistics.Encryption.setupPublicKey

import java.nio.charset.StandardCharsets
import java.security.spec.{EncodedKeySpec, X509EncodedKeySpec}
import java.security.{Key, KeyFactory, PublicKey}
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import scala.util.Try

object Encryption {
  private lazy val privateSymmetricKey: SecretKey = createSymmetricKey

  def encrypt(asymmetricPublicKey: PublicEncryptionKey, toEncode: String): Try[EncryptionResult] = {
    for {
      publicKey <- asymmetricPublicKey.publicKey
      toEncodeBytes           = toEncode.getBytes(StandardCharsets.UTF_8)
      encryptedBySymmetricKey = cipherAndEncode(toEncodeBytes, EncryptionType.AES, privateSymmetricKey)
      encryptedSymmetricKeyWithPublicRSA = cipherAndEncode(
        privateSymmetricKey.getEncoded,
        EncryptionType.RSA,
        publicKey
      )
    } yield EncryptionResult(encryptedSymmetricKeyWithPublicRSA, encryptedBySymmetricKey)

  }

  private def cipherAndEncode(toEncode: Array[Byte], encryptionType: EncryptionType.Value, key: Key): String = {
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

  private[statistics] def setupPublicKey(plainKey: Option[String]): Try[PublicKey] = {
    Try {
      val key                     = plainKey.get
      val publicKeyBytes          = Base64.getDecoder.decode(key)
      val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
      val keySpec: EncodedKeySpec = new X509EncodedKeySpec(publicKeyBytes)
      keyFactory.generatePublic(keySpec)
    }
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

final case class PublicEncryptionKey(plainPublicEncryptionKey: Option[String]) {
  lazy val publicKey: Try[PublicKey] = setupPublicKey(plainPublicEncryptionKey)
}
