package pl.touk.nussknacker.engine.kafka

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.KafkaUtils.sanitizeClientId

class KafkaUtilsTest extends AnyFunSuite with Matchers {

  test("sanitizes client.id") {
    sanitizeClientId("a b?c.d*e") shouldBe "a_b_c.d_e"
  }

}
