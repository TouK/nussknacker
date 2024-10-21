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
    ).forEvery { (expression, expected) =>
      evaluateAny(expression) shouldBe expected
    }

    Table(
      ("expression", "expected"),
      ("#CONV.toNumberOrNull(1)", "Number"),
    ).forEvery { (expression, expected) =>
      evaluateType(expression, types = Map.empty) shouldBe expected.valid
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
      ("#CONV.toJson('[{}]')", JList.of[JMap[Nothing, Nothing]](JMap.of())),
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
