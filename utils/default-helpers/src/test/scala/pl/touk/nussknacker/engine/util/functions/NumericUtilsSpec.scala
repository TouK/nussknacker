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

  test("sqrt should work for numeric arguments") {
    evaluateType("#NUMERIC.sqrt(4)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.sqrt(4)") shouldBe 2.0d

    evaluateAny("#NUMERIC.sqrt(4L)") shouldBe 2.0d
    evaluateAny("#NUMERIC.sqrt(4.0)") shouldBe 2.0d
  }

  test("cbrt should work for numeric arguments") {
    evaluateType("#NUMERIC.cbrt(8)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.cbrt(8)") shouldBe 2.0d

    evaluateAny("#NUMERIC.cbrt(8L)") shouldBe 2.0d
    evaluateAny("#NUMERIC.cbrt(8.0)") shouldBe 2.0d
  }

  test("pow should work for numeric arguments") {
    evaluateType("#NUMERIC.pow(2, 3)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.pow(2, 3)") shouldBe 8.0d

    evaluateAny("#NUMERIC.pow(2L, 3)") shouldBe 8.0d
    evaluateAny("#NUMERIC.pow(2, 3L)") shouldBe 8.0d
    evaluateAny("#NUMERIC.pow(2.0, 3.0)") shouldBe 8.0d
  }

  test("exp should work for numeric arguments") {
    evaluateType("#NUMERIC.exp(0)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.exp(0)") shouldBe 1.0d
  }

  test("log should work for numeric arguments") {
    evaluateType("#NUMERIC.log(1)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.log(1)") shouldBe 0.0d

    evaluateAny("#NUMERIC.log(1L)") shouldBe 0.0d
    evaluateAny("#NUMERIC.log(1.0)") shouldBe 0.0d
  }

  test("log10 should work for numeric arguments") {
    evaluateType("#NUMERIC.log10(1)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.log10(1)") shouldBe 0.0d

    evaluateAny("#NUMERIC.log10(1L)") shouldBe 0.0d
    evaluateAny("#NUMERIC.log10(1.0)") shouldBe 0.0d
  }

  test("sin cos tan should work for numeric arguments") {
    evaluateType("#NUMERIC.sin(0)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.sin(0)") shouldBe 0.0d
    evaluateAny("#NUMERIC.cos(0)") shouldBe 1.0d
    evaluateAny("#NUMERIC.tan(0)") shouldBe 0.0d

    evaluateAny("#NUMERIC.sin(0.0)") shouldBe 0.0d
    evaluateAny("#NUMERIC.cos(0.0)") shouldBe 1.0d
    evaluateAny("#NUMERIC.tan(0.0)") shouldBe 0.0d
  }

  test("asin acos atan should work for numeric arguments") {
    evaluateType("#NUMERIC.asin(0)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.asin(0)") shouldBe 0.0d
    evaluateAny("#NUMERIC.acos(1)") shouldBe 0.0d
    evaluateAny("#NUMERIC.atan(0)") shouldBe 0.0d
  }

  test("toRadians and toDegrees should work for numeric arguments") {
    evaluateType("#NUMERIC.toRadians(180)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.toRadians(180.0)") shouldBe Math.PI

    evaluateType("#NUMERIC.toDegrees(3.141592653589793)") shouldBe "Double".valid
    evaluateAny("#NUMERIC.toDegrees(3.141592653589793)") shouldBe 180.0d
  }

}
