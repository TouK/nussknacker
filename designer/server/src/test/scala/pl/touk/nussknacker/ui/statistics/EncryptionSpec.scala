//package pl.touk.nussknacker.ui.statistics
//
//import org.scalatest.EitherValues
//import org.scalatest.funsuite.AnyFunSuite
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.prop.TableDrivenPropertyChecks
//import pl.touk.nussknacker.test.PatientScalaFutures
//import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.{AES, EncryptionType, RSA}
//import pl.touk.nussknacker.ui.statistics.Encryption.{createSymmetricKey, encode, setupPublicKey}
//
//import java.nio.charset.StandardCharsets
//import java.security.{Key, KeyFactory}
//import java.security.spec.{EncodedKeySpec, PKCS8EncodedKeySpec}
//import java.util.Base64
//import javax.crypto.Cipher
//
//class EncryptionSpec
//    extends AnyFunSuite
//    with Matchers
//    with EitherValues
//    with PatientScalaFutures
//    with TableDrivenPropertyChecks {
//
//  test("Message before encryption and decryption should be the same") {
//    val message          = "random message with bigger and bigger length"
//    val secretKeyForTest = createSymmetricKey
//
//    val encryptedMessageAES = Encryption.encode(message, AES, secretKeyForTest)
//
//    val publicKey           = setupPublicKey(publicKeyForTest)
//    val encryptedMessageRSA = Encryption.encode(message, RSA, publicKey) todo czemu 2x szyfrujemy wiadomość najpierw AES, a potem RSA?
//
//    val decryptedAES = decode(encryptedMessageAES, AES, secretKeyForTest)
//
//    val privateKey   = setupPrivateKey(privateKeyForTest)
//    val decryptedRSA = decode(encryptedMessageRSA, RSA, privateKey)
//
//    decryptedAES shouldBe message
//    decryptedRSA shouldBe message todo ????
//
//    val secretKeyAsString = Base64.getEncoder.encodeToString(secretKeyForTest.getEncoded)
//    val encodedSecretKey  = encode(secretKeyAsString, RSA, publicKey)
//    val decodedSecretKey  = decode(encodedSecretKey, RSA, privateKey)
//
//    decodedSecretKey shouldBe secretKeyAsString
//  }
// todo: A czy nie powinniśmy sprawdzać takiego flow:
// 1. podanie klucza publicznego
// 2. wygenerowanie klucza symetrycznego
// 3. zaszyfrowanie wiadomości kluczem symetrycznym
// 4. zaszyfrowanie klucza symetrycznego kluczem publicznym
// 5. A w test odszyfrowanie zaszyfrowanych danych i sprawdzenie czy wiadomość się zgadza
//
//  test("Creating symmetric keys creates new one every time") {
//    val key1 = createSymmetricKey
//    val key2 = createSymmetricKey
//
//    key1 == key2 shouldBe false
//  }
//
//}
