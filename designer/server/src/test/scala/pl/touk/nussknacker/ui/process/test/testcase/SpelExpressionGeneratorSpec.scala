package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing

class SpelExpressionGeneratorSpec extends AnyFunSuite with Matchers {

  test("should generate sample expression for String") {
    val result = SpelExpressionGenerator.generate(typing.Typed[String])
    result shouldBe Some("'string'")
  }

  test("should generate sample expression for Integer") {
    val result = SpelExpressionGenerator.generate(typing.Typed[Integer])
    result shouldBe Some("42")
  }

  test("should generate sample expression for Long") {
    val result = SpelExpressionGenerator.generate(typing.Typed[java.lang.Long])
    result shouldBe Some("42")
  }

  test("should generate sample expression for Double") {
    val result = SpelExpressionGenerator.generate(typing.Typed[java.lang.Double])
    result shouldBe Some("42.0")
  }

  test("should generate sample expression for Boolean") {
    val result = SpelExpressionGenerator.generate(typing.Typed[java.lang.Boolean])
    result shouldBe Some("true")
  }

  test("should generate sample expression for record type") {
    val recordType = typing.Typed.record(
      Map(
        "name" -> typing.Typed[String],
        "age"  -> typing.Typed[Integer]
      )
    )
    val result = SpelExpressionGenerator.generate(recordType)
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
    val result = SpelExpressionGenerator.generate(nestedRecord)
    result shouldBe Some("{user: {name: 'string', active: true}, count: 42}")
  }

  test("should generate sample expression for List") {
    val listType = typing.Typed.genericTypeClass(classOf[java.util.List[_]], List(typing.Typed[String]))
    val result   = SpelExpressionGenerator.generate(listType)
    result shouldBe Some("{'string'}")
  }

  test("should generate sample expression for Map") {
    val mapType = typing.Typed.genericTypeClass(
      classOf[java.util.Map[_, _]],
      List(typing.Typed[String], typing.Typed[Integer])
    )
    val result = SpelExpressionGenerator.generate(mapType)
    result shouldBe Some("{'key': 42}")
  }

  test("should return null for Unknown type") {
    val result = SpelExpressionGenerator.generate(typing.Unknown)
    result shouldBe Some("null")
  }

}
