package pl.touk.nussknacker.engine.util.functions

import cats.implicits.catsSyntaxValidatedId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import java.math.{BigDecimal => JBigDecimal}
import java.util.{List => JList}
import java.util.{Map => JMap}

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
      ("#CONV.toBoolean(null)", null),
      ("#CONV.toBoolean(true)", true),
      ("#CONV.toBoolean('true')", true),
      ("#CONV.toBooleanOrNull(1)", null),
      ("#CONV.toBooleanOrNull(1.1)", null),
      ("#CONV.toBooleanOrNull('a')", null),
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
      "#CONV.toBoolean('a')",
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

  test("parse JSON") {
    Table(
      ("expression", "expected"),
      ("#CONV.toJson('null')", null),
      ("#CONV.toJson('\"str\"')", "str"),
      ("#CONV.toJson('1')", JBigDecimal.valueOf(1)),
      ("#CONV.toJson('true')", true),
      ("#CONV.toJson('[]')", JList.of()),
      ("#CONV.toJson('[{}]')", JList.of(JMap.of())),
      ("#CONV.toJson('[1, \"str\", true]')", JList.of(JBigDecimal.valueOf(1), "str", true)),
      ("#CONV.toJson('{}')", JMap.of()),
      (
        "#CONV.toJson('{ \"a\": 1, \"b\": true, \"c\": \"str\", \"d\": [], \"e\": {} }')",
        JMap.of(
          "a",
          JBigDecimal.valueOf(1),
          "b",
          true,
          "c",
          "str",
          "d",
          JList.of(),
          "e",
          JMap.of()
        )
      )
    ).forEvery { (expression, expected) =>
      evaluateAny(expression) shouldBe expected
    }

    Table(
      ("expression", "expected"),
      ("#CONV.toJson('{ \"a\": 1, \"b\": true, \"c\": \"str\", \"d\": [], \"e\": {} }')", "Unknown")
    ).forEvery { (expression, expected) =>
      evaluateType(expression, types = Map.empty) shouldBe expected.valid
    }
  }

  test("fail to parse JSON when invalid string is passed") {
    val caught = intercept[SpelExpressionEvaluationException] {
      evaluateAny("""#CONV.toJson('{ "a": 1 ')""")
    }
    caught.getMessage should include("""Cannot convert [{ "a": 1 ] to JSON""")
  }

  test("return null when parsing to JSON is not possible") {
    evaluateAny("""#CONV.toJsonOrNull('{ "a": 1 ')""") should be(null)
  }

  test("to stringified JSON") {
    Table(
      ("expression", "expected"),
      ("""#CONV.toJsonString(null)""", "null"),
      ("""#CONV.toJsonString('str')""", "\"str\""),
      ("""#CONV.toJsonString(1)""", "1"),
      ("""#CONV.toJsonString(true)""", "true"),
      ("""#CONV.toJsonString({})""", """[]"""),
      ("""#CONV.toJsonString({ 1, "str", true })""", """[1,"str",true]"""),
      (
        """#CONV.toJsonString({ a: 1, b: true, c: "str", d: {}, e: { f: 2 } })""",
        """{"e":{"f":2},"a":1,"b":true,"c":"str","d":[]}"""
      ),
    ).forEvery { (expression, expected) =>
      evaluateAny(expression) shouldBe expected
    }

    Table(
      ("expression", "expected"),
      ("""#CONV.toJsonString(null)""", "String"),
      ("""#CONV.toJsonString('str')""", "String"),
      ("""#CONV.toJsonString(1)""", "String"),
      ("""#CONV.toJsonString(true)""", "String"),
      ("""#CONV.toJsonString({})""", "String"),
      ("""#CONV.toJsonString({ 1, "str", true })""", "String"),
      ("""#CONV.toJsonString({ a: 1, b: true, c: "str", d: {}, e: { f: 2 } })""", "String"),
    ).forEvery { (expression, expected) =>
      evaluateType(expression, types = Map.empty) shouldBe expected.valid
    }
  }

}
