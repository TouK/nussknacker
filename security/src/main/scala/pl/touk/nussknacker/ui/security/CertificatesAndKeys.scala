package pl.touk.nussknacker.ui.security

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.security.{KeyFactory, PublicKey}
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object CertificatesAndKeys {

  def publicKeyFromString(keyStr: String, charset: Charset): PublicKey = {
    val keySpec = new X509EncodedKeySpec(decodeBase64(extractBase64EncodedPEM(keyStr), charset))
    KeyFactory.getInstance("RSA").generatePublic(keySpec)
  }

  def publicKeyFromStringCertificate(certStr: String, charset: Charset): PublicKey = {
    val certStream = new ByteArrayInputStream(decodeBase64(extractBase64EncodedPEM(certStr), charset))
    CertificateFactory.getInstance("X.509").generateCertificate(certStream).getPublicKey
  }

  def extractBase64EncodedPEM(pem: String): String = {
    // referring to https://tools.ietf.org/html/rfc7468#section-5.1
    val beginningPattern = "^-----BEGIN.*-----".r
    val endingPattern    = "-----END.*$".r
    beginningPattern.replaceFirstIn(endingPattern.replaceFirstIn(pem.replaceAll(System.lineSeparator, ""), ""), "")
  }

  def decodeBase64(encoded: String, charset: Charset): Array[Byte] = Base64.getDecoder.decode(encoded.getBytes(charset))

  def encodeBase64(bytes: Array[Byte]): String = Base64.getEncoder.encodeToString(bytes)

  def textualRepresentationOfPublicKey(publicKey: PublicKey): String =
    s"-----BEGIN PUBLIC KEY-----\n${encodeBase64(publicKey.getEncoded)}\n-----END PUBLIC KEY-----"
}
