package pl.touk.nussknacker.ui.security

import java.io.ByteArrayInputStream
import java.nio.charset.{Charset, StandardCharsets}
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

object CertificatesAndKeys {
  def publicKeyFromString(keyStr: String, charset: Charset): PublicKey = {
    val publicKey64 = keyStr
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replaceAll(System.lineSeparator, "")
      .replace("-----END PUBLIC KEY-----", "")

    val keySpec = new X509EncodedKeySpec(Base64.getDecoder.decode(publicKey64.getBytes(charset)))
    KeyFactory.getInstance("RSA").generatePublic(keySpec)
  }

  def publicKeyFromStringCertificate(certStr: String, charset: Charset): PublicKey = {
    val cert64 = certStr
      .replace("-----BEGIN CERTIFICATE-----", "")
      .replaceAll(System.lineSeparator, "")
      .replace("-----END CERTIFICATE-----", "")

    val certStream = new ByteArrayInputStream(Base64.getDecoder.decode(cert64.getBytes(charset)))
    CertificateFactory.getInstance("X.509").generateCertificate(certStream).getPublicKey
  }
}
