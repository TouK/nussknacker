package pl.touk.nussknacker.ui.validation

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralIntValidator, MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory}

class AdditionalPropertiesValidatorTest extends FunSuite with Matchers {
  private val reqFieldName = "propReq"
  private val optionalFieldName = "propOpt"
  private val optFixedFieldName = "propOptFixed"
  private val possibleValues = List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))
  private val label = "foo"

  private val validator = new AdditionalPropertiesValidator(
    TestFactory.mapProcessingTypeDataProvider(
      "streaming" -> Map(
        reqFieldName -> AdditionalPropertyConfig(
          None,
          None,
          Some(List(LiteralIntValidator, MandatoryParameterValidator)),
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

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("MissingRequiredProperty", _, _, Some(reqFieldName), NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("validate non empty config with empty required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> ""
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(
      NodeValidationError("InvalidLiteralIntValue", _, _, Some("propReq"), NodeValidationErrorType.SaveNotAllowed),
      NodeValidationError("MandatoryParameterValidator", _, _, Some("propReq"), NodeValidationErrorType.SaveAllowed)
      ) =>
    }
  }

  test("validate non empty config with required property with wrong type") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("InvalidLiteralIntValue", _, _, Some("propReq"), NodeValidationErrorType.SaveNotAllowed)) =>
    }
  }

  test("validate empty config") {
    val process = ProcessTestData.displayableWithAdditionalFields(None)

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("MissingRequiredProperty", _, _, Some(reqFieldName), NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("validate non empty config with fixed value property with wrong value") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        optFixedFieldName -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(
      NodeValidationError("InvalidPropertyFixedValue", _, _, Some(optFixedFieldName), NodeValidationErrorType.SaveNotAllowed),
      NodeValidationError("MissingRequiredProperty", _, _, Some(reqFieldName), NodeValidationErrorType.SaveAllowed)
      ) =>
    }
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

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("UnknownProperty", _, _, Some(unknownProperty), NodeValidationErrorType.SaveAllowed)) =>
    }
  }
}
