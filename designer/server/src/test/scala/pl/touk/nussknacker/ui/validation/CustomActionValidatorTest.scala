package pl.touk.nussknacker.ui.validation

import cats.data.{Validated, ValidatedNel}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{EmptyMandatoryParameter, MismatchParameter}
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  NotBlankParameterValidator,
  NotNullParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionParameter}
import pl.touk.nussknacker.restmodel.CustomActionRequest

class CustomActionValidatorTest extends AnyFunSuite with Matchers {

  private val testCustomActionName = ScenarioActionName("testCustomAction")

  private val testCustomActionParams =
    CustomActionParameter(
      "testParam1",
      StringParameterEditor,
      NotNullParameterValidator ::
        Nil
    ) ::
      CustomActionParameter(
        "testParam2",
        StringParameterEditor,
        NotBlankParameterValidator :: NotNullParameterValidator :: Nil
      ) :: Nil

  private val testCustomAction =
    CustomActionDefinition(
      testCustomActionName,
      "testStatus1" :: "testStatus2" :: Nil,
      testCustomActionParams
    )

  private val noParamsCustomAction =
    CustomActionDefinition(ScenarioActionName("noparams"), "testStatus" :: Nil, Nil)

  private val defaultValidator = new CustomActionValidator(testCustomAction)

  private def customActionRequest(params: Map[String, String]) = {
    CustomActionRequest(
      testCustomActionName,
      params
    )
  }

  private val validTestParams = Map(
    "testParam1" -> "validVal1",
    "testParam2" -> "validVal2"
  )

  test("should pass when validating correct data") {
    val result = defaultValidator.validateCustomActionParams(validTestParams)
    result should be(Validated.Valid(()))
  }

  test("should return Invalid when requestParams list is bigger than customActionParams list") {
    val invalidTestParamsTooManyParams = Map(
      "testParam1" -> "validVal1",
      "testParam2" -> "validVal2",
      "testParam3" -> "validVal3"
    )

    val result: ValidatedNel[PartSubGraphCompilationError, Unit] =
      defaultValidator.validateCustomActionParams(invalidTestParamsTooManyParams)
    result match {
      case Validated.Invalid(_) =>
      // pass
      case Validated.Valid(_) =>
        fail("Expected Invalid but got different result type")
    }

    val errorMessage = result.fold(
      nel => nel.toList.collectFirst { case a: MismatchParameter => a.message },
      _ => fail("Expected errors but no errors are on the list")
    )
    errorMessage.getOrElse(
      new IllegalStateException("should have message")
    ) shouldBe "Couldn't find a matching parameter in action definition for this param: " + "testParam3"
  }

  test("should return Invalid when missing parameters that are mandatory") {

    val mandatoryParamsActionParams =
      CustomActionParameter(
        "mandatory",
        StringParameterEditor,
        MandatoryParameterValidator ::
          Nil
      ) ::
        CustomActionParameter(
          "testParam2",
          StringParameterEditor,
          NotBlankParameterValidator :: NotNullParameterValidator :: Nil
        ) :: Nil

    val mandatoryParamsAction =
      CustomActionDefinition(
        testCustomActionName,
        "testStatus1" :: "testStatus2" :: Nil,
        mandatoryParamsActionParams
      )
    val invalidTestParamsTooFewParams = Map(
      "testParam2" -> "ValidVal2"
    )

    val customValidator = new CustomActionValidator(mandatoryParamsAction)

    val result: ValidatedNel[PartSubGraphCompilationError, Unit] =
      customValidator.validateCustomActionParams(invalidTestParamsTooFewParams)
    result match {
      case Validated.Invalid(_) =>
      // pass
      case Validated.Valid(_) =>
        fail("Expected Invalid but got different result type")
    }
  }

  test("should return Invalid when validating invalid data but proper request") {
    val invalidTestParamsInvalidValues = Map(
      "testParam1" -> null,
      "testParam2" -> "ValidVal2"
    )

    val result: ValidatedNel[PartSubGraphCompilationError, Unit] =
      defaultValidator.validateCustomActionParams(invalidTestParamsInvalidValues)
    result match {
      case Validated.Invalid(_) =>
      // pass
      case Validated.Valid(_) =>
        fail("Expected Invalid but got different result type")
    }

    val error = result.fold(
      nel => nel.head,
      _ => fail("Expected errors but no errors are on the list")
    )
    val expectedError = EmptyMandatoryParameter(
      "This field is required and can not be null",
      "Please fill field for this parameter",
      ParameterName("testParam1"),
      "testCustomAction"
    )
    error shouldBe expectedError
  }

  test("should return invalid when provided with incorrect param names") {
    val invalidTestParamsInvalidParamNames = Map(
      "testParam3" -> "validVal",
      "testParam2" -> "ValidVal2"
    )

    val result: ValidatedNel[PartSubGraphCompilationError, Unit] =
      defaultValidator.validateCustomActionParams(invalidTestParamsInvalidParamNames)
    result match {
      case Validated.Invalid(_) =>
      // pass
      case Validated.Valid(_) =>
        fail("Expected Invalid but got different result type")
    }

    val errorMessage = result.fold(
      nel => nel.toList.collectFirst { case a: MismatchParameter => a.message },
      _ => fail("Expected errors but no errors are on the list")
    )
    errorMessage.getOrElse(
      new IllegalStateException("should have message")
    ) shouldBe "Couldn't find a matching parameter in action definition for this param: testParam3"
  }

  test("should not fail when provided with empty params for no params action") {
    val validator       = new CustomActionValidator(noParamsCustomAction)
    val validTestParams = Map.empty[String, String]

    val result = validator.validateCustomActionParams(validTestParams)
    result should be(Validated.Valid(()))
  }

}
