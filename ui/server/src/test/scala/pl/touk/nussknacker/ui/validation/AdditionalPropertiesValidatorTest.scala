package pl.touk.nussknacker.ui.validation

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralParameterValidator, MandatoryParameterValidator, RegExpParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory}

import scala.collection.immutable.ListMap

class AdditionalPropertiesValidatorTest extends FunSuite with Matchers {
  private val reqFieldName = "propReq"
  private val regexpFieldName = "propRegExp"
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
          Some(List(LiteralParameterValidator.integerValidator, MandatoryParameterValidator)),
          Some(label)),
        regexpFieldName -> AdditionalPropertyConfig(
          None,
          None,
          Some(List(LiteralParameterValidator.numberValidator)),
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
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
        "propReq" -> "5"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors shouldBe List.empty
  }

  test("validate non empty config without required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
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
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
        "propReq" -> ""
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(
        NodeValidationError("EmptyMandatoryParameter", _, _, Some("propReq"), NodeValidationErrorType.SaveAllowed)
      ) =>
    }
  }

  test("validate regexp config with empty property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
        "propReq" -> "1",
        "propRegExp" -> ""
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List() =>
    }
  }

  test("validate config with invalid property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
        "propReq" -> "1",
        "propRegExp" -> "asd"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("MismatchParameter", _, _, Some("propRegExp"),  NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("validate non empty config with required property with wrong type") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
        "propReq" -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("InvalidIntegerLiteralParameter", _, _, Some("propReq"), NodeValidationErrorType.SaveAllowed)) =>
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
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
        optFixedFieldName -> "some text"
      ))
    ))

    val result = validator.validate(process)

    result.errors.processPropertiesErrors should matchPattern {
      case List(
      NodeValidationError("InvalidPropertyFixedValue", _, _, Some(optFixedFieldName), NodeValidationErrorType.SaveAllowed),
      NodeValidationError("MissingRequiredProperty", _, _, Some(reqFieldName), NodeValidationErrorType.SaveAllowed)
      ) =>
    }
  }

  test("validate non empty config with unknown property") {
    val unknownProperty = "unknown"
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = ListMap(
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
