package pl.touk.nussknacker.engine.language.dictWithLabel

import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue}

import scala.reflect.ClassTag

class DictKeyWithLabelExpressionParserTest extends AnyFunSuite with Matchers with OptionValues {
  private val dictKeyWithLabelExpressionParser = DictKeyWithLabelExpressionParser

  test("should parse dict key with label String expression and lift typing information") {
    checkForDictKeyWithLabelExpressionTypingInfo[String]("'pizza'", "pizza")
  }

  test("should parse dict key with label Long expression and lift typing information") {
    checkForDictKeyWithLabelExpressionTypingInfo[Long](42, "number")
  }

  test("should parse dict key with label Boolean expression and lift typing information") {
    checkForDictKeyWithLabelExpressionTypingInfo[Boolean](true, "judge")
  }

  private def checkForDictKeyWithLabelExpressionTypingInfo[T: ClassTag](key: T, label: String) = {
    val jsonString = s"""{"key":"$key","label":"$label"}"""
    val parsedTypingResult = dictKeyWithLabelExpressionParser
      .parse(jsonString, ValidationContext.empty, Typed.typedClass[T])
      .toOption
      .value
      .typingInfo
      .typingResult

    inside(parsedTypingResult) { case typedObjectWithValue: TypedObjectWithValue =>
      typedObjectWithValue.underlying shouldBe Typed.typedClass[T]
      typedObjectWithValue.valueOpt shouldBe Some(key)
    }
  }

}
