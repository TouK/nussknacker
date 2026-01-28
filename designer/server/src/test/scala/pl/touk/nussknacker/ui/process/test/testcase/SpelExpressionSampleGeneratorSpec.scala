package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing

class SpelExpressionSampleGeneratorSpec extends AnyFunSuite with Matchers {

  test("should generate sample expression for String") {
    val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[String])
    result shouldBe Some("'string'")
  }

  test("should generate sample expression for Integer") {
    val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[Integer])
    result shouldBe Some("42")
  }

  test("should generate sample expression for Long") {
    val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[java.lang.Long])
    result shouldBe Some("42")
  }

  test("should generate sample expression for Double") {
    val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[java.lang.Double])
    result shouldBe Some("42.0")
  }

  test("should generate sample expression for Boolean") {
    val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[java.lang.Boolean])
    result shouldBe Some("true")
  }

  test("should generate sample expression for record type") {
    val recordType = typing.Typed.record(
      Map(
        "name" -> typing.Typed[String],
        "age"  -> typing.Typed[Integer]
      )
    )
    val result = SpelExpressionSampleGenerator.generateSampleExpression(recordType)
    result shouldBe Some("{name: 'string', age: 42}")
  }

  test("should generate sample expression for nested record") {
    val nestedRecord = typing.Typed.record(
      Map(
        "user" -> typing.Typed.record(
          Map(
            "name"   -> typing.Typed[String],
            "active" -> typing.Typed[java.lang.Boolean]
          )
        ),
        "count" -> typing.Typed[Integer]
      )
    )
    val result = SpelExpressionSampleGenerator.generateSampleExpression(nestedRecord)
    result shouldBe Some("{user: {name: 'string', active: true}, count: 42}")
  }

  test("should generate sample expression for List") {
    val listType = typing.Typed.genericTypeClass(classOf[java.util.List[_]], List(typing.Typed[String]))
    val result   = SpelExpressionSampleGenerator.generateSampleExpression(listType)
    result shouldBe Some("{'string'}")
  }

  test("should generate sample expression for Map") {
    val mapType = typing.Typed.genericTypeClass(
      classOf[java.util.Map[_, _]],
      List(typing.Typed[String], typing.Typed[Integer])
    )
    val result = SpelExpressionSampleGenerator.generateSampleExpression(mapType)
    result shouldBe Some("{'key': 42}")
  }

  test("should return null for Unknown type") {
    val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Unknown)
    result shouldBe Some("null")
  }

}
