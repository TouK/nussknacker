package pl.touk.nussknacker.engine.language.json

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedNull, TypedObjectWithValue, Unknown}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.JsonParsingError
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.jdk.CollectionConverters._

class JsonParserTest extends AnyFunSuite with Matchers {

  private val parser = JsonParser

  test("Should parse an empty object") {
    val dataSample = "{}"
    val result     = parse(dataSample)
    result shouldBe Valid(Typed.record(List.empty))
  }

  test("Should parse integer") {
    val dataSample = "123"
    val result     = parse(dataSample)
    result shouldBe Valid(TypedObjectWithValue(Typed.typedClass[Int], 123))
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
          "age"  -> TypedObjectWithValue(Typed.typedClass[Int], 22),
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
        "age"  -> Typed.typedClass[Int],
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

    // TypingResult does not keep the order of fields in the Maps, so it is hard to assert with the TypingResult instance
    // display method sorts fields, so the order is deterministic
    val expectedDisplayedResult = parse(dataSample).map(_.withoutValue.display)
    expectedDisplayedResult shouldBe Valid(
      "Record{" +
        "arrayExample: List[Unknown], booleanExample: Boolean, integerExample: Integer, nullExample: Unknown, numberExample: BigDecimal, " +
        "objectExample: Record{nestedArray: List[Integer], nestedBoolean: Boolean, nestedInteger: Integer, nestedNumber: BigDecimal, " +
        "nestedObject: Record{deepKey: String}, nestedString: String}, stringExample: String" +
        "}"
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
    parsingErrors shouldBe NonEmptyList.of(JsonParsingError("expected ] or , got '{\"id\":...' (line 4, column 5)"))
  }

  private def parse(jsonString: String) = {
    parser.parse(jsonString, ValidationContext.empty, Unknown).map(_.returnType)
  }

}
