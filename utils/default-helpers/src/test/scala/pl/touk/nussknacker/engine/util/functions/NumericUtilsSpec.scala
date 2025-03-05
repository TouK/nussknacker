package pl.touk.nussknacker.engine.util.functions

import cats.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NumericUtilsSpec extends AnyFunSuite with BaseSpelSpec with Matchers {

  test("string to number conversion should return narrow numeric types in the runtime") {
    evaluateType("#NUMERIC.toNumber('42')") shouldBe "Number".valid
    evaluateAny("#NUMERIC.toNumber('42')") shouldBe 42

    evaluateType("#NUMERIC.toNumber('21355252552')") shouldBe "Number".valid
    evaluateAny("#NUMERIC.toNumber('21355252552')") shouldBe 21355252552L

    evaluateType("#NUMERIC.toNumber('53.32')") shouldBe "Number".valid
    evaluateAny("#NUMERIC.toNumber('53.32')") shouldBe 53.32d

    evaluateType("#NUMERIC.toNumber('12345678901234567890.12345678901234567890')") shouldBe "Number".valid
    evaluateAny("#NUMERIC.toNumber('12345678901234567890.12345678901234567890')") shouldBe 1.2345678901234567e19

    evaluateType("#NUMERIC.toNumber('12345678901234567890')") shouldBe "Number".valid
    evaluateAny("#NUMERIC.toNumber('12345678901234567890')") shouldBe 1.2345678901234567e19
  }

}
