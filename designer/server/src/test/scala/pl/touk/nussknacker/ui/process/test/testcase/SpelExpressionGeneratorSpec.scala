package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing._

class SpelExpressionGeneratorSpec extends AnyFunSuite with Matchers {

  test("should generate sample expression for String") {
    val result = SpelExpressionGenerator.generate(Typed[String])

    result shouldBe Some("'string'")
  }

  test("should generate sample expression for Integer") {
    val result = SpelExpressionGenerator.generate(Typed[Integer])

    result shouldBe Some("42")
  }

  test("should generate sample expression for Long") {
    val result = SpelExpressionGenerator.generate(Typed[java.lang.Long])

    result shouldBe Some("42")
  }

  test("should generate sample expression for Double") {
    val result = SpelExpressionGenerator.generate(Typed[java.lang.Double])

    result shouldBe Some("42.0")
  }

  test("should generate sample expression for Boolean") {
    val result = SpelExpressionGenerator.generate(Typed[java.lang.Boolean])

    result shouldBe Some("true")
  }

  test("should generate sample expression for record type") {
    val recordType = Typed.record(
      Map(
        "name" -> Typed[String],
        "age"  -> Typed[Integer]
      )
    )

    val result = SpelExpressionGenerator.generate(recordType)

    result shouldBe Some("{name: 'string', age: 42}")
  }

  test("should generate sample expression for nested record") {
    val nestedRecord = Typed.record(
      Map(
        "user" -> Typed.record(
          Map(
            "name"   -> Typed[String],
            "active" -> Typed[java.lang.Boolean]
          )
        ),
        "count" -> Typed[Integer]
      )
    )

    val result = SpelExpressionGenerator.generate(nestedRecord)

    result shouldBe Some("{user: {name: 'string', active: true}, count: 42}")
  }

  test("should generate sample expression for List") {
    val listType = Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[String]))

    val result = SpelExpressionGenerator.generate(listType)

    result shouldBe Some("{'string'}")
  }

  test("should generate sample expression for Map") {
    val mapType = Typed.genericTypeClass(
      classOf[java.util.Map[_, _]],
      List(Typed[String], Typed[Integer])
    )

    val result = SpelExpressionGenerator.generate(mapType)

    result shouldBe Some("{'key': 42}")
  }

  test("should return null for Unknown type") {
    val result = SpelExpressionGenerator.generate(Unknown)

    result shouldBe Some("null")
  }

  test("should generate sample expression for complex HTTP request/response record") {
    val headerRecordType = Typed.record(
      Map(
        "name"  -> Typed[String],
        "value" -> Typed[String]
      )
    )
    val requestType = Typed.record(
      Map(
        "body"    -> Typed.record(Map.empty[String, TypingResult]),
        "headers" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(headerRecordType)),
        "method"  -> Typed[String],
        "url"     -> Typed[String]
      )
    )
    val responseType = Typed.record(
      Map(
        "body"       -> Unknown,
        "headers"    -> Typed.genericTypeClass(classOf[java.util.List[_]], List(headerRecordType)),
        "statusCode" -> Typed[Integer],
        "statusText" -> Typed[String]
      )
    )
    val httpRecordType = Typed.record(
      Map(
        "request"  -> requestType,
        "response" -> responseType
      )
    )

    val result = SpelExpressionGenerator.generate(httpRecordType)

    result shouldBe Some(
      "{request: {body: {}, headers: {{name: 'string', value: 'string'}}, method: 'string', url: 'string'}, response: {body: null, headers: {{name: 'string', value: 'string'}}, statusCode: 42, statusText: 'string'}}"
    )
  }

}
