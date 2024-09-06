package pl.touk.nussknacker.engine.util.functions

import cats.implicits.catsSyntaxValidatedId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException

class ConversionUtilsSpec extends AnyFunSuite with BaseSpelSpec with Matchers {

  test("primitives conversions") {
    Table(
      ("expression", "expected"),
      ("#CONV.toNumberOrNull(1)", 1),
      ("#CONV.toNumberOrNull(1.1)", 1.1),
      ("#CONV.toNumberOrNull('a')", null),
      ("#CONV.toNumberOrNull(null)", null),
      ("#CONV.toNumberOrNull(true)", null),
      ("#CONV.toString(1)", "1"),
      ("#CONV.toString(1.1)", "1.1"),
      ("#CONV.toString('a')", "a"),
      ("#CONV.toString(null)", null),
      ("#CONV.toString(true)", "true"),
      ("#CONV.toBoolean('a')", false),
      ("#CONV.toBoolean(null)", null),
      ("#CONV.toBoolean(true)", true),
      ("#CONV.toBoolean('true')", true),
      ("#CONV.toBooleanOrNull(1)", null),
      ("#CONV.toBooleanOrNull(1.1)", null),
      ("#CONV.toBooleanOrNull('a')", false),
      ("#CONV.toBooleanOrNull(null)", null),
      ("#CONV.toBooleanOrNull(true)", true),
      ("#CONV.toBooleanOrNull('true')", true),
      ("#CONV.toInteger(1)", 1),
      ("#CONV.toInteger(1.1)", 1),
      ("#CONV.toInteger('1')", 1),
      ("#CONV.toInteger(null)", null),
      ("#CONV.toIntegerOrNull(1)", 1),
      ("#CONV.toIntegerOrNull(1.1)", 1),
      ("#CONV.toIntegerOrNull('1')", 1),
      ("#CONV.toIntegerOrNull('a')", null),
      ("#CONV.toIntegerOrNull(null)", null),
      ("#CONV.toIntegerOrNull(true)", null),
      ("#CONV.toIntegerOrNull('true')", null),
      ("#CONV.toLong(1)", 1),
      ("#CONV.toLong(1.1)", 1),
      ("#CONV.toLong('1')", 1),
      ("#CONV.toLong(null)", null),
      ("#CONV.toLongOrNull(1)", 1),
      ("#CONV.toLongOrNull(1.1)", 1),
      ("#CONV.toLongOrNull('1')", 1),
      ("#CONV.toLongOrNull('a')", null),
      ("#CONV.toLongOrNull(null)", null),
      ("#CONV.toLongOrNull(true)", null),
      ("#CONV.toLongOrNull('true')", null),
      ("#CONV.toDouble(1)", 1.0),
      ("#CONV.toDouble(1.1)", 1.1),
      ("#CONV.toDouble('1')", 1.0),
      ("#CONV.toDouble(null)", null),
      ("#CONV.toDoubleOrNull(1)", 1.0),
      ("#CONV.toDoubleOrNull(1.1)", 1.1),
      ("#CONV.toDoubleOrNull('1')", 1.0),
      ("#CONV.toDoubleOrNull('a')", null),
      ("#CONV.toDoubleOrNull(null)", null),
      ("#CONV.toDoubleOrNull(true)", null),
      ("#CONV.toDoubleOrNull('true')", null),
      ("#CONV.toBigInteger(1)", BigInt(1).bigInteger),
      ("#CONV.toBigInteger(1.1)", BigInt(1).bigInteger),
      ("#CONV.toBigInteger('1')", BigInt(1).bigInteger),
      ("#CONV.toBigInteger(null)", null),
      ("#CONV.toBigIntegerOrNull(1)", BigInt(1).bigInteger),
      ("#CONV.toBigIntegerOrNull(1.1)", BigInt(1).bigInteger),
      ("#CONV.toBigIntegerOrNull('1')", BigInt(1).bigInteger),
      ("#CONV.toBigIntegerOrNull('a')", null),
      ("#CONV.toBigIntegerOrNull(null)", null),
      ("#CONV.toBigIntegerOrNull(true)", null),
      ("#CONV.toBigIntegerOrNull('true')", null),
      ("#CONV.toBigDecimal(1)", BigDecimal(1).bigDecimal),
      ("#CONV.toBigDecimal(1.1)", BigDecimal(1.1).bigDecimal),
      ("#CONV.toBigDecimal('1')", BigDecimal(1).bigDecimal),
      ("#CONV.toBigDecimal(null)", null),
      ("#CONV.toBigDecimalOrNull(1)", BigDecimal(1).bigDecimal),
      ("#CONV.toBigDecimalOrNull(1.1)", BigDecimal(1.1).bigDecimal),
      ("#CONV.toBigDecimalOrNull('1')", BigDecimal(1).bigDecimal),
      ("#CONV.toBigDecimalOrNull('a')", null),
      ("#CONV.toBigDecimalOrNull(null)", null),
      ("#CONV.toBigDecimalOrNull(true)", null),
      ("#CONV.toBigDecimalOrNull('true')", null),
    ).forEvery { (expression, expected) =>
      evaluateAny(expression) shouldBe expected
    }

    Table(
      ("expression", "expected"),
      ("#CONV.toNumberOrNull(1)", "Number"),
      ("#CONV.toString(1)", "String"),
      ("#CONV.toBoolean('true')", "Boolean"),
      ("#CONV.toBooleanOrNull('true')", "Boolean"),
      ("#CONV.toInteger('true')", "Integer"),
      ("#CONV.toIntegerOrNull('true')", "Integer"),
      ("#CONV.toLong('true')", "Long"),
      ("#CONV.toLongOrNull('true')", "Long"),
      ("#CONV.toDouble('true')", "Double"),
      ("#CONV.toDoubleOrNull('true')", "Double"),
      ("#CONV.toBigInteger('true')", "BigInteger"),
      ("#CONV.toBigIntegerOrNull('true')", "BigInteger"),
      ("#CONV.toBigDecimal('true')", "BigDecimal"),
      ("#CONV.toBigDecimalOrNull('true')", "BigDecimal"),
    ).forEvery { (expression, expected) =>
      evaluateType(expression, types = Map.empty) shouldBe expected.valid
    }
  }

  test("should throw exception if a value cannot be converted to primitive") {
    Table(
      "expression",
      "#CONV.toBoolean(1)",
      "#CONV.toBoolean(1.1)",
      "#CONV.toInteger('a')",
      "#CONV.toInteger(true)",
      "#CONV.toInteger('true')",
      "#CONV.toLong('a')",
      "#CONV.toLong(true)",
      "#CONV.toLong('true')",
      "#CONV.toDouble('a')",
      "#CONV.toDouble(true)",
      "#CONV.toDouble('true')",
      "#CONV.toBigInteger('a')",
      "#CONV.toBigInteger(true)",
      "#CONV.toBigInteger('true')",
      "#CONV.toBigDecimal('a')",
      "#CONV.toBigDecimal(true)",
      "#CONV.toBigDecimal('true')",
    ).forEvery { expression =>
      val caught = intercept[SpelExpressionEvaluationException] {
        evaluateAny(expression, Map.empty)
      }
      caught.getMessage should (
        include("Cannot convert:") or
          include("is neither a decimal digit number") or
          include("For input string:")
      )
    }
  }

}
