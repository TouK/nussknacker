package pl.touk.nussknacker.test.utils

import pl.touk.nussknacker.ui.statistics.{EncryptionType, PublicEncryptionKey}

import java.nio.charset.StandardCharsets
import java.security.{Key, KeyFactory}
import java.security.spec.{EncodedKeySpec, PKCS8EncodedKeySpec}
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object StatisticEncryptionSupport {

  private val privateKeyForTest =
    "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDBxZLtcp68Jpw3ExiD3RwSMSZot2E6afCMYnY+EMzle3NdK+lwg/cSUoaetKJT" +
      "i0IZFShug4ehCNND0mAKf1F2+QbLC+GXrvTh0xVfQAzDruLJMjCSeXK9n2DIvxWcNdU1dkKi+VR7dafKM1oibPsD9sQhAWKfahcRq9TdfeEfVj" +
      "pMQmiPubgsZ+orzW4jAtCdJqpDW+bJwJpbGXtLokGanAWRqsGKH8uMyADMn29PmvTL9CLaBQbEA/4FNI8TRmYZtPMvjbZZ4zku0yug/5viG6F8" +
      "URrdwqUSecqgKN5PEsAE8mFPFsumXyUqyeROIcJ2cILq+pQQ7i9ttcYVv9a5AgMBAAECggEAGYFaZptzXgwC/JOB4ubOvsJDblMnnsW6XkVzj5" +
      "XXMnmTuiGBN53XKU09ilRNGDa4pjk3ujGL6lwRWthXwJSjnMezPN9n0UxmeqG11XTGeVdiuIg9BnqqlLUOyz2uUtfEeaYC/WquKRYCgPCjc5aU" +
      "BSCNDlRyrnhgvaMNgMivQi10xsKZyvFxbZr8h0CoRhJxEykPy0Fl6krzq6TIhBl5WPMXbY3EVjrueD+dxYh5sUZI9uiJNp3o8AhWP5stb0+bUw" +
      "S1LHBiOk49kZav83xKbItN5pvV7hxgjRm1OJXFnd/Vil4uvY4XRpfHxUPyEcHjedfg9JPWfYcDKxqki2nKpQKBgQD3mJVO8DgaZxo1WNUzD/qm" +
      "L9NKmp5JS0I7h6KfseygvGFqs3CzQLkOPOw3eD13L+M84Q0felaY1TqDbGDnU8CUaSNytcvxEDsO7L+ZaFDAjA7V06hT0dbiCGlfJhFDCRaQZe" +
      "MBNdVvYAjT79s3u09BDxqeBYXWi9omfyNrkDmP7wKBgQDIWUzA51YehCGh9mXhRWvYUFt3gntctZ3q7mWrm2Q2WKbLZljxReQ7GH/RcllEbgZf" +
      "wK108QOGfyzY9dSVe4hQF+a3pl+6qa+v1mHpRH89C9O5Y/nJ2P8pHM/iLzVizKnuFub7e6r28EsRqWCNj3hXVIorobhuORxxQGK+NqJb1wKBgQ" +
      "C+/qI22ZG9tEkAW/1ivXHR3tS0GR//VCHMgC3P2NW7jHXtdGx8qnHWbOKfS47GsVcusECKRrokfV/8347FA9Nr6MrigyDGXYnhUnneohyg26na" +
      "AsM3nYiQpoJ6Vog8WPdh/malmVyC5OcKbl2B2Wkiu78BpSsAPLPUvICbSg9NWwKBgQDDrLQSE99Anrxae1S12B+kfZdX+dCSk69QrNYP7QzLJ3" +
      "qAXc2wmBp4vUzZO12EQBFpo73F4T8YNU2P+UlPVf+kejOUWqJUpTY5/qBSO9wpu0tmXgaw5YcQmFtRBdqWTF4Lc8LVC8vGspoe/daXa/CVGrR4" +
      "ONLwlEb7NW87aBDasQKBgQDMqCYKejLKQ9R3XvEi203PQNHMn0CNDva2kBWCWPFbBQ2zqQJXSEV9ygEBH0pXxu4lctz5NDEbRooMhVfLwYS/P/" +
      "2d0NcxtlFTJAW/iaknqoPd64C5ZIBMu4sZqzoGoydUannB4Zg4QP7U5+gnSmx/5Ry6FD6aerKDuo0lbWbcNw=="

  private lazy val privateKey = setupPrivateKey(privateKeyForTest)

  private val publicKeyForTest =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwcWS7XKevCacNxMYg90cEjEmaLdhOmnwjGJ2PhDM5XtzXSvpcIP3ElKGnrSiU4tCGRUo" +
      "boOHoQjTQ9JgCn9RdvkGywvhl6704dMVX0AMw67iyTIwknlyvZ9gyL8VnDXVNXZCovlUe3WnyjNaImz7A/bEIQFin2oXEavU3X3hH1Y6TEJoj7" +
      "m4LGfqK81uIwLQnSaqQ1vmycCaWxl7S6JBmpwFkarBih/LjMgAzJ9vT5r0y/Qi2gUGxAP+BTSPE0ZmGbTzL422WeM5LtMroP+b4huhfFEa3cKl" +
      "EnnKoCjeTxLABPJhTxbLpl8lKsnkTiHCdnCC6vqUEO4vbbXGFb/WuQIDAQAB"

  lazy val publicKey: PublicEncryptionKey = PublicEncryptionKey(publicKeyForTest)

  def decode(urlString: String): Map[String, String] = {
    val queryParams = QueryParamsHelper.extractFromURLString(urlString)
    (queryParams.get("encryptionKey"), queryParams.get("encryptedParams")) match {
      case (Some(encodedSymmetricKey), Some(encodedQueryParams)) =>
        val decoded = decode(encodedSymmetricKey, encodedQueryParams)
        QueryParamsHelper.extractFromURLString(decoded)
      case _ =>
        throw new IllegalArgumentException("Cannot decode query params")
    }
  }

  def decode(symmetricKeyToDecode: String, queryParamsToDecode: String): String = {
    val decodedSymmetricKey = decode(symmetricKeyToDecode, privateKey, EncryptionType.RSA)
    val symmetricKey        = new SecretKeySpec(decodedSymmetricKey, "AES")
    val decodedQueryParams  = decode(queryParamsToDecode, symmetricKey, EncryptionType.AES)
    new String(decodedQueryParams, StandardCharsets.UTF_8)
  }

  def decodeToString(urlString: String): String = {
    val queryParams = QueryParamsHelper.extractFromURLString(urlString)
    (queryParams.get("encryptionKey"), queryParams.get("encryptedParams")) match {
      case (Some(encodedSymmetricKey), Some(encodedQueryParams)) =>
        decode(encodedSymmetricKey, encodedQueryParams)
      case _ =>
        throw new IllegalArgumentException("Cannot decode query params")
    }
  }

  private def decode(toDecode: String, key: Key, encryptionType: EncryptionType.Value): Array[Byte] = {
    val cipher: Cipher = Cipher.getInstance(encryptionType.encryption)
    cipher.init(Cipher.DECRYPT_MODE, key)
    cipher.doFinal(Base64.getDecoder.decode(toDecode))
  }

  private def setupPrivateKey(plainKey: String) = {
    val privateKeyBytes         = Base64.getDecoder.decode(plainKey)
    val keyFactory: KeyFactory  = KeyFactory.getInstance("RSA")
    val keySpec: EncodedKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes)
    keyFactory.generatePrivate(keySpec)
  }

}
