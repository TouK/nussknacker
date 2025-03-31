package pl.touk.nussknacker.engine.language.json

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.JsonParsingError
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class JsonParserTest extends AnyFunSuite with Matchers {

  private val parser = JsonParser

  test("should parse JSON expression") {
    val validJson = """|[
                       |  {
                       |    "products": [
                       |      {"id": 1, "name": "Laptop", "price": 1200.00},
                       |      {"id": 2, "name": "Smartphone", "price": 800.50},
                       |      {"id": 3, "name": "Tablet", "price": 450.75}
                       |    ]
                       |  },
                       |  {
                       |    "someObject": {
                       |      "someString": "some string value",
                       |      "someBoolean": true,
                       |      "someNumber": 21.37
                       |    }
                       |  }
                       |]""".stripMargin

    val resultType = parser.parse(validJson, ValidationContext.empty, Unknown).validValue.typingInfo.typingResult
    // TODO: Right now we just use Unknown but we should create appropriate typing for Jsons in the future
    resultType shouldBe Unknown
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
    val parsingErrors = parser.parse(invalidJson, ValidationContext.empty, Unknown).invalidValue
    parsingErrors.size shouldBe 1
    parsingErrors.head shouldBe JsonParsingError("expected ] or , got '{\"id\":...' (line 4, column 5)")
  }

}
