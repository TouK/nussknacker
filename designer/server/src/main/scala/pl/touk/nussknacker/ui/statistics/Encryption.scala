package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.ui.statistics.EncryptionType.EncryptionType

import java.nio.charset.StandardCharsets
import java.security.spec.{EncodedKeySpec, X509EncodedKeySpec}
import java.security.{Key, KeyFactory, PublicKey}
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

// todo error handling
object Encryption {
  private lazy val privateSymmetricKey: SecretKey = createSymmetricKey

  // publicKey should passed with his own type and should have lazily initialized PublicKey
  def encrypt(plainAsymmetricPublicKey: String, toEncode: String): EncryptionResult = {
    val publicKey               = setupPublicKey(plainAsymmetricPublicKey)
    val toEncodeBytes           = toEncode.getBytes(StandardCharsets.UTF_8)
    val encryptedBySymmetricKey = cipherAndEncode(toEncodeBytes, EncryptionType.AES, privateSymmetricKey)
    val encryptedSymmetricKeyWithPublicRSA =
      cipherAndEncode(privateSymmetricKey.getEncoded, EncryptionType.RSA, publicKey)
    EncryptionResult(encryptedSymmetricKeyWithPublicRSA, encryptedBySymmetricKey)
  }

  private def cipherAndEncode(toEncode: Array[Byte], encryptionType: EncryptionType, key: Key): String = {
    val cipher: Cipher = Cipher.getInstance(encryptionType.toString)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encoded = cipher.doFinal(toEncode)
    Base64.getEncoder.encodeToString(encoded)
  }

  private[statistics] def createSymmetricKey: SecretKey = {
    val keyGenerator: KeyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256)
    keyGenerator.generateKey()
  }

  private[statistics] def setupPublicKey(plainKey: String): PublicKey = {
    val publicKeyBytes          = Base64.getDecoder.decode(plainKey)
    val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
    val keySpec: EncodedKeySpec = new X509EncodedKeySpec(publicKeyBytes)
    keyFactory.generatePublic(keySpec)
  }

}

final case class EncryptionResult(encryptedSymmetricKey: String, encryptedValue: String)

object EncryptionType extends Enumeration {
  type EncryptionType = Value

  val RSA, AES = Value
}
