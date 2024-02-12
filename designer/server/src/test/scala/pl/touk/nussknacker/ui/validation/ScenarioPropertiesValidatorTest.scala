package pl.touk.nussknacker.ui.validation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}

class ScenarioPropertiesValidatorTest extends AnyFunSuite with Matchers {
  private val reqFieldName      = "propReq"
  private val regexpFieldName   = "propRegExp"
  private val optionalFieldName = "propOpt"
  private val optFixedFieldName = "propOptFixed"
  private val possibleValues    = List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))
  private val label             = "foo"

  // TODO: tests for user privileges
  private implicit val user: LoggedUser = AdminUser("admin", "admin")

  private val validator = new ScenarioPropertiesValidator(
    Map(
      reqFieldName -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = None,
        validators = Some(List(LiteralIntegerValidator, MandatoryParameterValidator)),
        label = Some(label)
      ),
      regexpFieldName -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = None,
        validators = Some(List(LiteralIntegerValidator)),
        label = Some(label)
      ),
      optionalFieldName -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(StringParameterEditor),
        validators = None,
        label = Some(label)
      ),
      optFixedFieldName -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(FixedValuesParameterEditor(possibleValues)),
        validators = Some(List(FixedValuesValidator(possibleValues))),
        label = Some(label)
      )
    )
  )

  test("validate non empty config with required property") {
    val result = validator.validate(
      Map(
        "propReq" -> "5"
      ).toList
    )

    result.errors.processPropertiesErrors shouldBe List.empty
  }

  test("validate non empty config without required property") {
    val result = validator.validate(
      Map(
        "propOpt" -> "a"
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "MissingRequiredProperty",
              _,
              _,
              Some(reqFieldName),
              NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
  }

  test("validate non empty config with empty required property") {
    val result = validator.validate(
      Map(
        "propReq" -> ""
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError("EmptyMandatoryParameter", _, _, Some("propReq"), NodeValidationErrorType.SaveAllowed)
          ) =>
    }
  }

  test("validate regexp config with empty property") {
    val result = validator.validate(
      Map(
        "propReq"    -> "1",
        "propRegExp" -> ""
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern { case List() =>
    }
  }

  test("validate config with invalid property") {
    val result = validator.validate(
      Map(
        "propReq"    -> "1",
        "propRegExp" -> "asd"
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "InvalidIntegerLiteralParameter",
              _,
              _,
              Some("propRegExp"),
              NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
  }

  test("validate non empty config with required property with wrong type") {
    val result = validator.validate(
      Map(
        "propReq" -> "some text"
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "InvalidIntegerLiteralParameter",
              _,
              _,
              Some("propReq"),
              NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
  }

  test("validate empty config") {
    val result = validator.validate(List.empty)

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "MissingRequiredProperty",
              _,
              _,
              Some(reqFieldName),
              NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
  }

  test("validate non empty config with fixed value property with wrong value") {
    val result = validator.validate(
      Map(
        optFixedFieldName -> "some text"
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "InvalidPropertyFixedValue",
              _,
              _,
              Some(optFixedFieldName),
              NodeValidationErrorType.SaveAllowed
            ),
            NodeValidationError(
              "MissingRequiredProperty",
              _,
              _,
              Some(reqFieldName),
              NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
  }

  test("validate non empty config with unknown property") {
    val unknownProperty = "unknown"
    val result = validator.validate(
      Map(
        "propReq"       -> "5",
        unknownProperty -> "some text"
      ).toList
    )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError("UnknownProperty", _, _, Some(`unknownProperty`), NodeValidationErrorType.SaveAllowed)
          ) =>
    }
  }

}
