package pl.touk.nussknacker.engine.util.functions

import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RandomSpec extends AnyFunSuite with Matchers {

  private val repeatCount = 1000

  test("should return double between 0.0 (inclusive) and 1.0 (exclusive)") {
    repeat {
      random.nextDouble should (be >= 0.0 and be < 1.0)
    }
  }

  test("should return always true for success rate = 1.0") {
    repeat {
      random.nextBooleanWithSuccessRate(1.0) shouldBe true
    }
  }

  test("should return always false for success rate = 0.0") {
    repeat {
      random.nextBooleanWithSuccessRate(0.0) shouldBe false
    }
  }

  private def repeat(assertion: => Assertion): Unit = {
    (1 to repeatCount).foreach { _ => assertion }
  }

}
