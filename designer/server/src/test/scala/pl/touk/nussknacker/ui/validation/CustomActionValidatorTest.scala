package pl.touk.nussknacker.ui.validation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.{
  NotBlankParameterValidator,
  NotNullParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.deployment.{
  CustomActionDefinition,
  CustomActionParameter,
  CustomActionValidationResult
}
import pl.touk.nussknacker.restmodel.CustomActionRequest

class CustomActionValidatorTest extends AnyFunSuite with Matchers {

  private val testCustomActionName = "testCustomAction"

  private val testCustomActionParams =
    CustomActionParameter("testParam1", StringParameterEditor, Some(NotNullParameterValidator :: Nil)) ::
      CustomActionParameter(
        "testParam2",
        StringParameterEditor,
        Some(NotBlankParameterValidator :: NotNullParameterValidator :: Nil)
      ) :: Nil

  private val testCustomAction =
    CustomActionDefinition(
      ScenarioActionName(testCustomActionName),
      "testStatus1" :: "testStatus2" :: Nil,
      testCustomActionParams
    )

  private val noParamsCustomAction =
    CustomActionDefinition(ScenarioActionName("noparams"), "testStatus" :: Nil, Nil)

  private val validator = new CustomActionValidator(noParamsCustomAction :: testCustomAction :: Nil)

  type TestParams = Map[String, String]

  private def customActionRequest(params: TestParams) = {
    CustomActionRequest(
      ScenarioActionName(testCustomActionName),
      Some(params)
    )
  }

  private val validTestParams = Map(
    "testParam1" -> "validVal1",
    "testParam2" -> "validVal2"
  )

  private val validRequest = customActionRequest(validTestParams)

  test("should pass when validating correct data") {
    val result = validator.validateCustomActionParams(validRequest)
    result should be(Right(CustomActionValidationResult.Valid))
  }

  test("should fail(return left) when requestParams list doesn't match customActionParams list") {
    val invalidTestParamsTooFewParams = Map(
      "testParam1" -> "validVal1"
    )

    val invalidRequestTooFewParams = customActionRequest(invalidTestParamsTooFewParams)

    val result: Either[CustomActionValidationError, CustomActionValidationResult] =
      validator.validateCustomActionParams(invalidRequestTooFewParams)
    result match {
      case Left(_: MismatchedParamsError) =>
      // pass
      case Left(_) | Right(_) =>
        fail("Expected Left[MismatchedParamsError] but got different result type")
    }

    result.left.getOrElse(fail("should be left and have message")).getMessage shouldBe
      "Validation requires different count of custom action parameters than provided in request for: " + invalidRequestTooFewParams.actionName
  }

  test("should return Right(Invalid) when validating invalid data but proper request") {
    val invalidTestParamsInvalidValues = Map(
      "testParam1" -> null,
      "testParam2" -> "ValidVal2"
    )

    val invalidRequestInvalidValues = customActionRequest(invalidTestParamsInvalidValues)

    val result: Either[CustomActionValidationError, CustomActionValidationResult] =
      validator.validateCustomActionParams(invalidRequestInvalidValues)
    result match {
      case Right(_: CustomActionValidationResult.Invalid) =>
      // pass
      case Left(_) | Right(_) =>
        fail("Expected Right[Invalid] but got different result type")
    }

    result.getOrElse(fail("should be right and have message")).toString shouldBe
      s"Invalid(Map(testParam1 -> List(EmptyMandatoryParameter(This field is required and can not be null,Please fill field for this parameter,${ParameterName("testParam1")},testCustomAction)), testParam2 -> List()))"
  }

  test("should fail(return left) when provided with incorrect param names") {
    val invalidTestParamsInvalidParamNames = Map(
      "testParam3" -> "validVal",
      "testParam2" -> "ValidVal2"
    )

    val invalidRequestInvalidValues = customActionRequest(invalidTestParamsInvalidParamNames)
    val result: Either[CustomActionValidationError, CustomActionValidationResult] =
      validator.validateCustomActionParams(invalidRequestInvalidValues)
    result match {
      case Left(_: MismatchedParamsError) =>
      // pass
      case Left(_) | Right(_) =>
        fail("Expected Left[MismatchedParamsError] but got different result type")
    }

    result.left.getOrElse(fail("should be left and have message")).getMessage shouldBe
      "Missing params: testParam1 for action: " + invalidRequestInvalidValues.actionName
  }

  test("should not fail when provided with empty params for no params action") {
    val validTestParams = None

    val validRequest = CustomActionRequest(ScenarioActionName("noparams"), validTestParams)

    val result = validator.validateCustomActionParams(validRequest)
    result should be(Right(CustomActionValidationResult.Valid))
  }

  test("should fail(return left) when trying to validate a non existing action") {
    val nonExistingActionRequest = CustomActionRequest(
      ScenarioActionName("notInAnyDBHere"),
      None
    )

    val result: Either[CustomActionValidationError, CustomActionValidationResult] =
      validator.validateCustomActionParams(nonExistingActionRequest)
    result match {
      case Left(_: CustomActionNonExistingError) =>
      // pass
      case Left(_) | Right(_) =>
        fail("Expected Left[CustomActionNonExistingError] but got different result type")
    }

    result.left.getOrElse(fail("should be left and have message")).getMessage shouldBe
      s"Couldn't find this action: ${nonExistingActionRequest.actionName.toString}"

  }

}
