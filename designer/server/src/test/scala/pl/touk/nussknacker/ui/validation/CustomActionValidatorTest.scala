package pl.touk.nussknacker.ui.validation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.{
  NotBlankParameterValidator,
  NotNullParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionParameter}
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
    val result: Unit = validator.validateCustomActionParams(validRequest)
    result should be((): Unit)
  }

  test("should fail when requestParams list doesn't match customActionParams list") {
    val invalidTestParamsTooFewParams = Map(
      "testParam1" -> "validVal1"
    )

    val invalidRequestTooFewParams = customActionRequest(invalidTestParamsTooFewParams)

    val caughtException = intercept[CustomActionValidationError] {
      validator.validateCustomActionParams(invalidRequestTooFewParams)
    }

    caughtException.getMessage shouldBe
      "Different count of custom action parameters than provided in request for: " + invalidRequestTooFewParams.actionName
  }

  test("should fail when validating incorrect data") {
    val invalidTestParamsInvalidValues = Map(
      "testParam1" -> null,
      "testParam2" -> "ValidVal2"
    )

    val invalidRequestInvalidValues = customActionRequest(invalidTestParamsInvalidValues)

    val caughtException = intercept[CustomActionValidationError] {
      validator.validateCustomActionParams(invalidRequestInvalidValues)
    }

    caughtException.getMessage shouldBe
      "EmptyMandatoryParameter(This field is required and can not be null,Please fill field for this parameter,testParam1,testCustomAction)"
  }

  test("should fail when provided with incorrect param names") {
    val invalidTestParamsInvalidParamNames = Map(
      "testParam3" -> "validVal",
      "testParam2" -> "ValidVal2"
    )

    val invalidRequestInvalidValues = customActionRequest(invalidTestParamsInvalidParamNames)

    val caughtException = intercept[CustomActionValidationError] {
      validator.validateCustomActionParams(invalidRequestInvalidValues)
    }

    caughtException.getMessage shouldBe
      "No such parameter should be defined for this action: " + testCustomAction.name
  }

  test("should not fail when provided with empty params for no params action") {
    val validTestParams = None

    val validRequest = CustomActionRequest(ScenarioActionName("noparams"), validTestParams)

    val result: Unit = validator.validateCustomActionParams(validRequest)
    result should be((): Unit)

  }

}
