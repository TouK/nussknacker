package pl.touk.nussknacker.engine.language.json

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedNull, TypedObjectWithValue, Unknown}
import pl.touk.nussknacker.engine.expression.ExpectedType
import pl.touk.nussknacker.engine.expression.parse.ExpressionParser.BadTypeExpressionParseError
import pl.touk.nussknacker.engine.language.json.JsonParser.JsonDecodingError
import pl.touk.nussknacker.engine.language.json.JsonParsingFailureToExpressionParseErrorConverter.JsonParseError
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.time.LocalDate
import scala.jdk.CollectionConverters._

class JsonParserTest extends AnyFunSuite with Matchers with LoneElement {

  private val parser = JsonParser

  test("Should parse an empty object") {
    val dataSample = "{}"
    val result     = parse(dataSample)
    result shouldBe Valid(Typed.record(List.empty))
  }

  test("Should parse integer") {
    val dataSample = "123"
    val result     = parse(dataSample)
    result shouldBe Valid(TypedObjectWithValue(Typed.typedClass[Long], 123L))
  }

  test("Should parse floating number") {
    val dataSample = "3.14"
    val result     = parse(dataSample)
    result shouldBe Valid(
      TypedObjectWithValue(Typed.typedClass[java.math.BigDecimal], new java.math.BigDecimal("3.14"))
    )
  }

  test("Should parse string") {
    val dataSample = "\"text\""
    val result     = parse(dataSample)
    result shouldBe Valid(TypedObjectWithValue(Typed.typedClass[String], "text"))
  }

  test("Should parse an empty array") {
    val dataSample = "[]"
    val result     = parse(dataSample)
    result shouldBe Valid(
      TypedObjectWithValue(Typed.genericTypeClass(classOf[java.util.List[_]], List(Unknown)), List.empty[Any].asJava)
    )
  }

  test("Should parse boolean") {
    val dataSample = "false"
    val result     = parse(dataSample)
    result shouldBe Valid(TypedObjectWithValue(Typed.typedClass[Boolean], false))
  }

  test("Should parse null") {
    val dataSample = "null"
    val result     = parse(dataSample)
    result shouldBe Valid(TypedNull)
  }

  test("Should parse object") {
    val dataSample =
      s"""
         |{
         |  "name": "Tom",
         |  "age": 22,
         |  "city": "Warsaw"
         |}
         |""".stripMargin
    val result = parse(dataSample)
    result shouldBe Valid(
      Typed.record(
        List(
          "name" -> TypedObjectWithValue(Typed.typedClass[String], "Tom"),
          "age"  -> TypedObjectWithValue(Typed.typedClass[Long], 22L),
          "city" -> TypedObjectWithValue(Typed.typedClass[String], "Warsaw")
        )
      )
    )
  }

  test("Should parse array") {
    val dataSample =
      s"""
         |[
         |  {
         |    "name": "Tom",
         |    "age": 22,
         |    "city": "Warsaw"
         |  }
         |]
         |""".stripMargin

    val recordType = Typed.record(
      List(
        "name" -> Typed.typedClass[String],
        "age"  -> Typed.typedClass[Long],
        "city" -> Typed.typedClass[String]
      )
    )
    val expectedListType = Typed.genericTypeClass[java.util.List[_]](List(recordType))
    val result           = parse(dataSample)
    result shouldBe Valid(
      TypedObjectWithValue(
        expectedListType,
        List(
          Map(
            "name" -> "Tom",
            "age"  -> 22,
            "city" -> "Warsaw"
          ).asJava
        ).asJava
      )
    )
  }

  test("Should parse complex object") {
    val dataSample =
      s"""
         |{
         |  "stringExample": "exampleText",
         |  "numberExample": 42.5,
         |  "integerExample": 100,
         |  "booleanExample": true,
         |  "nullExample": null,
         |  "arrayExample": [
         |    "one",
         |    2,
         |    false,
         |    null,
         |    {"nestedKey": "nestedValue"}
         |  ],
         |  "objectExample": {
         |    "nestedString": "nestedText",
         |    "nestedNumber": 3.14,
         |    "nestedInteger": 2,
         |    "nestedBoolean": false,
         |    "nestedArray": [1, 2, 3],
         |    "nestedObject": {
         |      "deepKey": "deepValue"
         |    }
         |  }
         |}""".stripMargin

    val expressionResultType = parse(dataSample).validValue
    expressionResultType.withoutValue shouldBe Typed.record(
      Seq(
        "stringExample"  -> Typed[String],
        "numberExample"  -> Typed[java.math.BigDecimal],
        "integerExample" -> Typed[Long],
        "booleanExample" -> Typed[Boolean],
        "nullExample"    -> Unknown,
        "arrayExample"   -> Typed.fromDetailedType[java.util.List[_]],
        "objectExample" -> Typed.record(
          Seq(
            "nestedString"  -> Typed[String],
            "nestedNumber"  -> Typed[java.math.BigDecimal],
            "nestedInteger" -> Typed[Long],
            "nestedBoolean" -> Typed[Boolean],
            "nestedArray"   -> Typed.fromDetailedType[java.util.List[Long]],
            "nestedObject" -> Typed.record(
              Seq(
                "deepKey" -> Typed[String],
              )
            ),
          )
        )
      )
    )
  }

  test("should respect expected type") {
    val compiledExpression =
      parser.parse("\"2025-01-01\"", ValidationContext.empty, ExpectedType.loose(Typed[LocalDate])).validValue
    compiledExpression.returnType.withoutValue shouldBe Typed[LocalDate]

    parser
      .parse("\"illegal-date\"", ValidationContext.empty, ExpectedType.loose(Typed[LocalDate]))
      .invalidValue shouldBe NonEmptyList.of(
      JsonDecodingError("DecodingFailure at : Text 'illegal-date' could not be parsed at index 0")
    )
  }

  test("should return error when JSON cannot be parsed") {
    // Missing comma after Laptop object entry
    val invalidJson = """|{
                         |  "products": [
                         |    {"id": 1, "name": "Laptop", "price": 1200.00}
                         |    {"id": 2, "name": "Smartphone", "price": 800.50},
                         |    {"id": 3, "name": "Tablet", "price": 450.75}
                         |  ]
                         |}""".stripMargin
    val parsingErrors = parse(invalidJson).invalidValue
    parsingErrors shouldBe NonEmptyList.of(
      JsonParseError(
        "expected ] or , got '{\"id\":...'",
        Some(CoordinatesBasedTextRange(TextCoordinates(4, 3), TextCoordinates(5, 3)))
      )
    )
  }

  test("should verify expected type") {
    val expectedType = Typed.record(Seq("foo" -> Typed[String]))
    val parsingError =
      parser.parse("{}", ValidationContext.empty, ExpectedType.loose(expectedType)).invalidValue.toList.loneElement
    parsingError shouldBe BadTypeExpressionParseError(expectedType, Typed.record(Seq.empty))
  }

  private def parse(jsonString: String) = {
    parser.parse(jsonString, ValidationContext.empty, ExpectedType.loose(Unknown)).map(_.returnType)
  }

}
