package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.StatisticEncryptionSupport.{decode, publicKeyForTest}

class EncryptionSpec extends AnyFreeSpecLike with Matchers with EitherValues {

  "Encryption should be able to encrypt and decrypt" in {
    val queryParams =
      "a_n=0&a_t=0&a_v=0&c_n=0&ca=0&co_t=1&co_v=2&d_u=0&fr_m=0&fr_v=0&n_m=0&n_ma=0&n_mi=0&n_v=0&s_a=0&s_dm_c=0&s_dm_e=0&s_dm_f=0&s_dm_l=0&s_f=0&s_pm_b=0&s_pm_rr=0&s_pm_s=0&s_s=0&source=sources&u_ma=0&u_mi=0&u_v=3&v_m=0&v_ma=0&v_mi=0&v_v=0&version=1.17.0-SNAPSHOT"
    val publicKey = PublicEncryptionKey(publicKeyForTest)

    val encryptionResult = Encryption
      .encrypt(publicKey, queryParams)
      .value

    decode(encryptionResult.encryptedSymmetricKey, encryptionResult.encryptedValue) shouldBe queryParams
  }

}
