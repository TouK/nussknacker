package pl.touk.nussknacker.engine.json.swagger

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SwaggerObjectSpec extends AnyFunSuite with Matchers {

  private val definition = SwaggerObject(
    elementType = Map(
      "field1"            -> SwaggerString,
      "decimalField"      -> SwaggerBigDecimal,
      "doubleField"       -> SwaggerDouble,
      "nullField"         -> SwaggerNull,
      "mapField"          -> SwaggerObject(Map.empty),
      "mapOfStringsField" -> SwaggerObject(Map.empty, AdditionalPropertiesEnabled(SwaggerString))
    ),
    AdditionalPropertiesEnabled(SwaggerString),
    PatternWithSwaggerTyped("^.*Date", SwaggerDate) :: PatternWithSwaggerTyped("^.*Double", SwaggerDouble) :: Nil
  )

  private val definitionWithoutAdditionalProperties =
    definition.copy(additionalProperties = AdditionalPropertiesDisabled)

  test("should properly extract SwaggerType for given field") {

    definition.fieldSwaggerTypeByKey("field1") shouldBe Some(SwaggerString)
    definition.fieldSwaggerTypeByKey("decimalField") shouldBe Some(SwaggerBigDecimal)
    definition.fieldSwaggerTypeByKey("myDate") shouldBe Some(SwaggerDate)
    definition.fieldSwaggerTypeByKey("myDouble") shouldBe Some(SwaggerDouble)
    definition.fieldSwaggerTypeByKey("whatever") shouldBe Some(SwaggerString)

  }

  test("should return None if field is not specified ") {

    definitionWithoutAdditionalProperties.fieldSwaggerTypeByKey("whatever") shouldBe None

  }

}
