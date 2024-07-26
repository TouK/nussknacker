package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.EncryptionType

import java.nio.charset.StandardCharsets
import java.security.{Key, KeyFactory, PublicKey, SecureRandom}
import java.security.spec.{EncodedKeySpec, PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

object Encryption {
  private val plainPublicKey: String =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhiVyC/SBAie86jFktE5/MX2s5+j98d4PeaFV5YAwY12BNJ4Tn36x4lw5htKK/6dbQbbUIJ6aSpQsKIetQxfgqqpF9N6mOABUzpX6VmlZHvvNfV/9+ka7HGxBBwpNza15fGshiaCFkPfyOCSYb1HRWBoy+7beI08WKEC3wM1IXCgQZWUxYADyu4qSRHTMSVJAd1eyho50fc/LamnlZ4go5dK5HZSibDgUWyzodF45z7zugh5VvKeyLYPlLKa8YZMuEetH6LvR2Ag6WpYOUYgx2D8m25o4o3HWvDYJ67/gQ/Ty/UNsYHD/5bCpR7kMn4SJNK8zfDGymzozfsVmjTCNfwIDAQAB"
  val nuPublicKey: PublicKey = setupPublicKey(plainPublicKey)

  def encode(toEncode: String, encryptionType: EncryptionType, key: Key): String = {
    val cipher: Cipher = Cipher.getInstance(encryptionType.toString)
    cipher.init(Cipher.ENCRYPT_MODE, key)

    val encoded = cipher.doFinal(toEncode.getBytes(StandardCharsets.UTF_8))
    Base64.getEncoder.encodeToString(encoded)
  }

  def decode(toDecode: String, encryptionType: EncryptionType, key: Key): String = {
    val cipher: Cipher = Cipher.getInstance(encryptionType.toString)
    cipher.init(Cipher.DECRYPT_MODE, key)

    val decoded = cipher.doFinal(Base64.getDecoder.decode(toDecode))
    new String(decoded, StandardCharsets.UTF_8)
  }

  private[statistics] def createSymmetricKey: SecretKey = {
    lazy val random                = new SecureRandom()
    val keyGenerator: KeyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256, random)
    keyGenerator.generateKey()
  }

  private[statistics] def setupPublicKey(plainKey: String) = {
    val publicKeyBytes          = Base64.getDecoder.decode(plainKey)
    val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
    val keySpec: EncodedKeySpec = new X509EncodedKeySpec(publicKeyBytes)
    keyFactory.generatePublic(keySpec)
  }

  private[statistics] def setupPrivateKey(plainKey: String) = {
    val privateKeyBytes         = Base64.getDecoder.decode(plainKey)
    val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
    val keySpec: EncodedKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes)
    keyFactory.generatePrivate(keySpec)
  }

  object EncryptionTypes extends Enumeration {
    type EncryptionType = Value

    val RSA, AES = Value
  }

}
