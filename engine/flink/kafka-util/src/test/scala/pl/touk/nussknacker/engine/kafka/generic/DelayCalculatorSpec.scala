package pl.touk.nussknacker.engine.kafka.generic

import org.scalatest.{FunSuite, Matchers}

class DelayCalculatorSpec extends FunSuite with Matchers {

  test("should calculate fixed delay") {
    val fixedDelayCalculator = new FixedDelayCalculator(1000)
    fixedDelayCalculator.calculateDelay(5000, 4000) shouldBe 0
    fixedDelayCalculator.calculateDelay(5000, 4500) shouldBe 500
    fixedDelayCalculator.calculateDelay(5000, 5000) shouldBe 1000
  }
}
