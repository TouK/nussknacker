package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.{AES, RSA}

import java.util.Base64

class EncryptionSpec
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  test("encrypt") {
    val message = "random message with bigger and bigger length"
    println(Base64.getEncoder.encodeToString(Encryption.getSymmetricKey.getEncoded))
    println(Base64.getEncoder.encodeToString(Encryption.getSymmetricKey.getEncoded))
    println(Base64.getEncoder.encodeToString(Encryption.getSymmetricKey.getEncoded))
    val encryptedMessageAES = Encryption.encode(message, AES)
    val encryptedMessageRSA = Encryption.encode(message, RSA)

    println(encryptedMessageAES)
    println(encryptedMessageRSA)
  }

}
