package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.{AES, RSA}
import pl.touk.nussknacker.ui.statistics.Encryption.{createSymmetricKey, setupPrivateKey, setupPublicKey}

class EncryptionSpec
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  private val publicKeyForTest =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwcWS7XKevCacNxMYg90cEjEmaLdhOmnwjGJ2PhDM5XtzXSvpcIP3ElKGnrSiU4tCGRUoboOHoQjTQ9JgCn9RdvkGywvhl6704dMVX0AMw67iyTIwknlyvZ9gyL8VnDXVNXZCovlUe3WnyjNaImz7A/bEIQFin2oXEavU3X3hH1Y6TEJoj7m4LGfqK81uIwLQnSaqQ1vmycCaWxl7S6JBmpwFkarBih/LjMgAzJ9vT5r0y/Qi2gUGxAP+BTSPE0ZmGbTzL422WeM5LtMroP+b4huhfFEa3cKlEnnKoCjeTxLABPJhTxbLpl8lKsnkTiHCdnCC6vqUEO4vbbXGFb/WuQIDAQAB"
  private val privateKeyForTest =
    "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDBxZLtcp68Jpw3ExiD3RwSMSZot2E6afCMYnY+EMzle3NdK+lwg/cSUoaetKJTi0IZFShug4ehCNND0mAKf1F2+QbLC+GXrvTh0xVfQAzDruLJMjCSeXK9n2DIvxWcNdU1dkKi+VR7dafKM1oibPsD9sQhAWKfahcRq9TdfeEfVjpMQmiPubgsZ+orzW4jAtCdJqpDW+bJwJpbGXtLokGanAWRqsGKH8uMyADMn29PmvTL9CLaBQbEA/4FNI8TRmYZtPMvjbZZ4zku0yug/5viG6F8URrdwqUSecqgKN5PEsAE8mFPFsumXyUqyeROIcJ2cILq+pQQ7i9ttcYVv9a5AgMBAAECggEAGYFaZptzXgwC/JOB4ubOvsJDblMnnsW6XkVzj5XXMnmTuiGBN53XKU09ilRNGDa4pjk3ujGL6lwRWthXwJSjnMezPN9n0UxmeqG11XTGeVdiuIg9BnqqlLUOyz2uUtfEeaYC/WquKRYCgPCjc5aUBSCNDlRyrnhgvaMNgMivQi10xsKZyvFxbZr8h0CoRhJxEykPy0Fl6krzq6TIhBl5WPMXbY3EVjrueD+dxYh5sUZI9uiJNp3o8AhWP5stb0+bUwS1LHBiOk49kZav83xKbItN5pvV7hxgjRm1OJXFnd/Vil4uvY4XRpfHxUPyEcHjedfg9JPWfYcDKxqki2nKpQKBgQD3mJVO8DgaZxo1WNUzD/qmL9NKmp5JS0I7h6KfseygvGFqs3CzQLkOPOw3eD13L+M84Q0felaY1TqDbGDnU8CUaSNytcvxEDsO7L+ZaFDAjA7V06hT0dbiCGlfJhFDCRaQZeMBNdVvYAjT79s3u09BDxqeBYXWi9omfyNrkDmP7wKBgQDIWUzA51YehCGh9mXhRWvYUFt3gntctZ3q7mWrm2Q2WKbLZljxReQ7GH/RcllEbgZfwK108QOGfyzY9dSVe4hQF+a3pl+6qa+v1mHpRH89C9O5Y/nJ2P8pHM/iLzVizKnuFub7e6r28EsRqWCNj3hXVIorobhuORxxQGK+NqJb1wKBgQC+/qI22ZG9tEkAW/1ivXHR3tS0GR//VCHMgC3P2NW7jHXtdGx8qnHWbOKfS47GsVcusECKRrokfV/8347FA9Nr6MrigyDGXYnhUnneohyg26naAsM3nYiQpoJ6Vog8WPdh/malmVyC5OcKbl2B2Wkiu78BpSsAPLPUvICbSg9NWwKBgQDDrLQSE99Anrxae1S12B+kfZdX+dCSk69QrNYP7QzLJ3qAXc2wmBp4vUzZO12EQBFpo73F4T8YNU2P+UlPVf+kejOUWqJUpTY5/qBSO9wpu0tmXgaw5YcQmFtRBdqWTF4Lc8LVC8vGspoe/daXa/CVGrR4ONLwlEb7NW87aBDasQKBgQDMqCYKejLKQ9R3XvEi203PQNHMn0CNDva2kBWCWPFbBQ2zqQJXSEV9ygEBH0pXxu4lctz5NDEbRooMhVfLwYS/P/2d0NcxtlFTJAW/iaknqoPd64C5ZIBMu4sZqzoGoydUannB4Zg4QP7U5+gnSmx/5Ry6FD6aerKDuo0lbWbcNw=="

  test("Message before encryption and decryption should be the same") {
    val message          = "random message with bigger and bigger length"
    val secretKeyForTest = createSymmetricKey

    val encryptedMessageAES = Encryption.encode(message, AES, secretKeyForTest)

    val publicKey           = setupPublicKey(publicKeyForTest)
    val encryptedMessageRSA = Encryption.encode(message, RSA, publicKey)

    val decryptedAES = Encryption.decode(encryptedMessageAES, AES, secretKeyForTest)

    val privateKey   = setupPrivateKey(privateKeyForTest)
    val decryptedRSA = Encryption.decode(encryptedMessageRSA, RSA, privateKey)

    decryptedAES shouldBe message
    decryptedRSA shouldBe message
  }

  test("Creating symmetric keys creates new one every time") {
    val key1 = createSymmetricKey
    val key2 = createSymmetricKey

    key1 == key2 shouldBe false
  }

}
