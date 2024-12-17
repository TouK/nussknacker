package pl.touk.nussknacker.engine.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.springframework.util.NumberUtilsConsts

class MathUtilsSpec extends AnyFunSuite with Matchers {

  test("min for nulls") {
    MathUtils.min(null, null) shouldEqual null
    MathUtils.min(null, 1) shouldEqual 1

    val minForByte = MathUtils.min(null, 1.byteValue())
    minForByte.getClass shouldEqual classOf[java.lang.Byte]
    minForByte shouldBe 1.byteValue()
  }

  test("min for not nulls") {
    MathUtils.min(1, 2) shouldEqual 1

    val minForIntAndDouble = MathUtils.min(1, 2d)
    minForIntAndDouble.getClass shouldEqual classOf[java.lang.Double]
    minForIntAndDouble shouldEqual 1d

    val minForIntAndBigDecimal = MathUtils.min(1, java.math.BigDecimal.valueOf(2))
    minForIntAndBigDecimal.getClass shouldEqual classOf[java.math.BigDecimal]
    minForIntAndBigDecimal shouldEqual java.math.BigDecimal.valueOf(1)
  }

  test("max") {
    MathUtils.max(1, 2) shouldEqual 2
  }

  test("sum for nulls") {
    MathUtils.sum(null, null) shouldEqual null
    MathUtils.sum(null, 1) shouldEqual 1

    val minForByte = MathUtils.sum(null, 1.byteValue())
    minForByte.getClass shouldEqual classOf[java.lang.Integer]
    minForByte shouldBe 1
  }

  test("sum for not nulls") {
    MathUtils.sum(1, 0) shouldEqual 1
    MathUtils.sum(1, 2) shouldEqual 3

    val sumForIntAndDouble = MathUtils.sum(1, 2d)
    sumForIntAndDouble.getClass shouldEqual classOf[java.lang.Double]
    sumForIntAndDouble shouldEqual 3d

    val sumForIntAndBigDecimal = MathUtils.sum(1, java.math.BigDecimal.valueOf(2))
    sumForIntAndBigDecimal.getClass shouldEqual classOf[java.math.BigDecimal]
    sumForIntAndBigDecimal shouldEqual java.math.BigDecimal.valueOf(3)

    val sumForBytes = MathUtils.sum(1.byteValue(), 2.byteValue())
    sumForBytes.getClass shouldEqual classOf[java.lang.Integer]
    sumForBytes shouldEqual 3
  }

  test("max should use big decimal conversion which sets default scale") {
    val result = MathUtils.max(1, new java.math.BigDecimal(0))
    result.asInstanceOf[java.math.BigDecimal].scale() shouldBe NumberUtilsConsts.DEFAULT_BIG_DECIMAL_SCALE
  }

}
