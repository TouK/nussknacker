package pl.touk.nussknacker.engine.kafka.source.delayed

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DelayCalculatorSpec extends AnyFunSuite with Matchers {

  test("should calculate fixed delay") {
    val fixedDelayCalculator = new FixedDelayCalculator(1000)
    fixedDelayCalculator.calculateDelay(5000, 4000) shouldBe 0
    fixedDelayCalculator.calculateDelay(5000, 4500) shouldBe 500
    fixedDelayCalculator.calculateDelay(5000, 5000) shouldBe 1000
  }
}
