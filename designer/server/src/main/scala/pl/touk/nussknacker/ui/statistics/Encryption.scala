package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.{AES, EncryptionType, RSA}

import java.nio.charset.StandardCharsets
import java.security.{KeyFactory, PrivateKey, PublicKey, SecureRandom}
import java.security.spec.{EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

object Encryption {
  private val plainPublicKey: String =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhiVyC/SBAie86jFktE5/MX2s5+j98d4PeaFV5YAwY12BNJ4Tn36x4lw5htKK/6dbQbbUIJ6aSpQsKIetQxfgqqpF9N6mOABUzpX6VmlZHvvNfV/9+ka7HGxBBwpNza15fGshiaCFkPfyOCSYb1HRWBoy+7beI08WKEC3wM1IXCgQZWUxYADyu4qSRHTMSVJAd1eyho50fc/LamnlZ4go5dK5HZSibDgUWyzodF45z7zugh5VvKeyLYPlLKa8YZMuEetH6LvR2Ag6WpYOUYgx2D8m25o4o3HWvDYJ67/gQ/Ty/UNsYHD/5bCpR7kMn4SJNK8zfDGymzozfsVmjTCNfwIDAQAB"
  private val publicKey = setupPublicKey(plainPublicKey)

  val encryptRSACipher: Cipher = Cipher.getInstance("RSA")
  encryptRSACipher.init(Cipher.ENCRYPT_MODE, publicKey)

  private lazy val secretKey: SecretKey = createSymmetricKey

  val encryptAESCipher: Cipher = Cipher.getInstance("AES")
  encryptAESCipher.init(Cipher.ENCRYPT_MODE, secretKey)

  def getSymmetricKey: SecretKey = {
    secretKey
  }

  def encode(toEncode: String, encryptionType: EncryptionType): String = {
    val cipher = encryptionType match {
      case RSA => encryptRSACipher
      case AES => encryptAESCipher
    }
    try {
      val encoded = cipher.doFinal(toEncode.getBytes(StandardCharsets.UTF_8))
      Base64.getEncoder.encodeToString(encoded)
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        ""
    }
  }

//  Mostly for test purposes, normally statistics should use provided public key
  def encode(toEncode: String, encryptionType: EncryptionType, ownKey: String): String = {
    val cipher: Cipher = Cipher.getInstance(encryptionType.toString)
    val key = encryptionType match {
      case RSA => setupPublicKey(ownKey)
      case AES =>
        val keyBytes = Base64.getDecoder.decode(ownKey)
        new SecretKeySpec(keyBytes, "AES")
    }

    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encoded = cipher.doFinal(toEncode.getBytes(StandardCharsets.UTF_8))
    Base64.getEncoder.encodeToString(encoded)
  }

  private def createSymmetricKey: SecretKey = {
    lazy val random                = new SecureRandom()
    val keyGenerator: KeyGenerator = KeyGenerator.getInstance("AES")
    keyGenerator.init(256, random)
    keyGenerator.generateKey()
  }

  private def setupPublicKey(plainKey: String) = {
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
