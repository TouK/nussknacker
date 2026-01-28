package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing

class SpelExpressionSampleGeneratorSpec extends AnyFreeSpecLike with Matchers {

  "SpelExpressionSampleGenerator" - {

    "should generate sample expression for String" in {
      val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[String])
      result shouldBe Some("'string'")
    }

    "should generate sample expression for Integer" in {
      val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[Integer])
      result shouldBe Some("42")
    }

    "should generate sample expression for Long" in {
      val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[java.lang.Long])
      result shouldBe Some("42")
    }

    "should generate sample expression for Double" in {
      val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[java.lang.Double])
      result shouldBe Some("42.0")
    }

    "should generate sample expression for Boolean" in {
      val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Typed[java.lang.Boolean])
      result shouldBe Some("true")
    }

    "should generate sample expression for record type" in {
      val recordType = typing.Typed.record(
        Map(
          "name" -> typing.Typed[String],
          "age"  -> typing.Typed[Integer]
        )
      )
      val result = SpelExpressionSampleGenerator.generateSampleExpression(recordType)
      result shouldBe Some("{name: 'string', age: 42}")
    }

    "should generate sample expression for nested record" in {
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

    "should generate sample expression for List" in {
      val listType = typing.Typed.genericTypeClass(classOf[java.util.List[_]], List(typing.Typed[String]))
      val result   = SpelExpressionSampleGenerator.generateSampleExpression(listType)
      result shouldBe Some("{'string'}")
    }

    "should generate sample expression for Map" in {
      val mapType = typing.Typed.genericTypeClass(
        classOf[java.util.Map[_, _]],
        List(typing.Typed[String], typing.Typed[Integer])
      )
      val result = SpelExpressionSampleGenerator.generateSampleExpression(mapType)
      result shouldBe Some("{'key': 42}")
    }

    "should return null for Unknown type" in {
      val result = SpelExpressionSampleGenerator.generateSampleExpression(typing.Unknown)
      result shouldBe Some("null")
    }

  }

}
