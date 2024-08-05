package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.EncryptionType

import java.nio.charset.StandardCharsets
import java.security.{Key, KeyFactory, PublicKey}
import java.security.spec.{EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

object Encryption {
  private val plainPublicKey: String =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjdvfEFH8jBF56UZmtQvUH1gUCnRkHTke/jnOEIjdSmSqoyWGJ9UKC/PgLYyqLqNRKq2eEmLr26tQRaIoOHLcdGlGZXIrRkWHAPH8QTGrDt4Qm/COB8BPS7oV2tATsUN7zJNWfVRNDzcunzDwAtZKs4SDTsFLAPrZ5CKMt5JK9Q7Xrzekl5PunzJEuIOmlWusFanIqCgs0d245NVRKhbkqq/JkoseEB4sDXFwNyO7sO51aLgDST+P/G+tPveQpMusbGBK48Syce93R6bTf0Bd8KGaJQWvJPjD6rbX+K3vSQHqgb+NjIyUAWWNHuFvRt2NQ5iHsuaBU0B/61W8y6oMxQIDAQAB"
  lazy val nuPublicKey: PublicKey = setupPublicKey(plainPublicKey)

  def encode(toEncode: String, encryptionType: EncryptionType, key: Key): String = {
    val cipher: Cipher = Cipher.getInstance(encryptionType.toString)
    cipher.init(Cipher.ENCRYPT_MODE, key)

    val encoded = cipher.doFinal(toEncode.getBytes(StandardCharsets.UTF_8))
    Base64.getEncoder.encodeToString(encoded)
  }

  private[statistics] def createSymmetricKey: SecretKey = {
    val keyGenerator: KeyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256)
    keyGenerator.generateKey()
  }

  private[statistics] def setupPublicKey(plainKey: String) = {
    val publicKeyBytes          = Base64.getDecoder.decode(plainKey)
    val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
    val keySpec: EncodedKeySpec = new X509EncodedKeySpec(publicKeyBytes)
    keyFactory.generatePublic(keySpec)
  }

  object EncryptionTypes extends Enumeration {
    type EncryptionType = Value

    val RSA, AES = Value
  }

}
