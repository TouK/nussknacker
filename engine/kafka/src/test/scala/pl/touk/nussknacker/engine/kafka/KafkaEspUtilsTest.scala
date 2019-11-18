package pl.touk.nussknacker.engine.kafka

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.KafkaEspUtils.sanitizeClientId

class KafkaEspUtilsTest extends FunSuite with Matchers {

  test("sanitizes client.id") {
    sanitizeClientId("a b?c.d*e") shouldBe "a_b_c.d_e"
  }

}
