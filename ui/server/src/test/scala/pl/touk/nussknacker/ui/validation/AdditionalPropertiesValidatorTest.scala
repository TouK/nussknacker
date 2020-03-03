package pl.touk.nussknacker.ui.validation

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralIntValidator, MandatoryValueValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData

class AdditionalPropertiesValidatorTest extends FunSuite with Matchers {
  private val reqFieldName = "propReq"
  private val optionalFieldName = "propOpt"
  private val optFixedFieldName = "propOptFixed"
  private val possibleValues = List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))
  private val label = "foo"

  private val validator = new AdditionalPropertiesValidator(
    Map(
      "streaming" -> Map(
        reqFieldName -> AdditionalPropertyConfig(
          None,
          None,
          Some(List(LiteralIntValidator, MandatoryValueValidator)),
          Some(label)),
        optionalFieldName -> AdditionalPropertyConfig(
          None,
          Some(StringParameterEditor),
          None,
          Some(label)),
        optFixedFieldName -> AdditionalPropertyConfig(
          None,
          Some(FixedValuesParameterEditor(possibleValues)),
          Some(List(FixedValuesValidator(possibleValues))),
          Some(label)
        )
      ))
  )

  test("validate non empty config with required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> "5"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List.empty
  }

  test("validate non empty config without required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propOpt" -> "a"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List(missingRequiredProperty)
  }

  test("validate non empty config with empty required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> ""
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List(
      NodeValidationError(
        "InvalidLiteralIntValue",
        s"Property $reqFieldName ($label) has value of invalid type",
        s"Expected integer, got: ''.",
        fieldName = Some(reqFieldName),
        errorType = NodeValidationErrorType.SaveNotAllowed),
      NodeValidationError(
        "EmptyMandatoryParameter",
        s"Empty expression for mandatory parameter",
        s"Please fill expression for this parameter",
        Some(reqFieldName),
        NodeValidationErrorType.SaveAllowed),
    )
  }

  test("validate non empty config with required property with wrong type") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List(
      NodeValidationError(
        "InvalidLiteralIntValue",
        s"Property $reqFieldName ($label) has value of invalid type",
        s"Expected integer, got: 'some text'.",
        fieldName = Some(reqFieldName),
        errorType = NodeValidationErrorType.SaveNotAllowed
      ))
  }

  test("validate empty config") {
    val process = ProcessTestData.displayableWithAdditionalFields(None)

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List(missingRequiredProperty)
  }

  test("validate non empty config with fixed value property with wrong value") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        optFixedFieldName -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List(
      NodeValidationError(
        "InvalidPropertyFixedValue",
        s"Property $optFixedFieldName ($label) has invalid value",
        s"Expected one of a, b, got: 'some text'.",
        Some(optFixedFieldName),
        NodeValidationErrorType.SaveNotAllowed),
      missingRequiredProperty
    )
  }

  test("validate non empty config with unknown property") {
    val unknownProperty = "unknown"
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> "5",
        "unknown" -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List(
      NodeValidationError(
        "UnknownProperty",
        s"Unknown property $unknownProperty",
        s"Property $unknownProperty is not known",
        Some(unknownProperty),
        NodeValidationErrorType.SaveAllowed
      )
    )
  }

  private def missingRequiredProperty = {
    NodeValidationError(
      "MissingRequiredProperty",
      s"Configured property $reqFieldName ($label) is missing",
      s"Please fill missing property $reqFieldName ($label)",
      Some(reqFieldName),
      NodeValidationErrorType.SaveAllowed
    )
  }
}
