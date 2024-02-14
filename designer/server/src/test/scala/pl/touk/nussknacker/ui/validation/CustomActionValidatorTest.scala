package pl.touk.nussknacker.ui.validation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.{
  NotBlankParameterValidator,
  NotNullParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment.{CustomAction, CustomActionParameter, CustomActionRequest}
import pl.touk.nussknacker.engine.deployment.User

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
    CustomAction(testCustomActionName, "testStatus1" :: "testStatus2" :: Nil, testCustomActionParams)

  type TestParams = Map[String, String]

  private def customActionRequest(params: TestParams) = {
    CustomActionRequest(
      testCustomActionName,
      ProcessVersion.empty,
      User("testId", "testUser"),
      params
    )
  }

  private val validTestParams = Map(
    "testParam1" -> "validVal1",
    "testParam2" -> "validVal2"
  )

  private val validRequest = customActionRequest(validTestParams)

  test("should pass when validating correct data") {
    val result: Unit = CustomActionValidator.validateCustomActionParams(validRequest, testCustomAction)
    result should be((): Unit)
  }

  test("should fail when requestParams list doesn't match customActionParams list") {
    val invalidTestParamsTooFewParams = Map(
      "testParam1" -> "validVal1"
    )

    val invalidRequestTooFewParams = customActionRequest(invalidTestParamsTooFewParams)

    val caughtException = intercept[Exception] {
      CustomActionValidator.validateCustomActionParams(invalidRequestTooFewParams, testCustomAction)
    }

    caughtException.getMessage shouldBe
      "Different count of custom action parameters than provided in request for: " + invalidRequestTooFewParams.name
  }

  test("should fail when validating incorrect data") {
    val invalidTestParamsInvalidValues = Map(
      "testParam1" -> null,
      "testParam2" -> "ValidVal2"
    )

    val invalidRequestInvalidValues = customActionRequest(invalidTestParamsInvalidValues)

    val caughtException = intercept[Exception] {
      CustomActionValidator.validateCustomActionParams(invalidRequestInvalidValues, testCustomAction)
    }

    caughtException.getMessage shouldBe
      "This field is required and can not be null"
  }

  test("should fail when provided with incorrect param names") {
    val invalidTestParamsInvalidParamNames = Map(
      "testParam3" -> "validVal",
      "testParam2" -> "ValidVal2"
    )

    val invalidRequestInvalidValues = customActionRequest(invalidTestParamsInvalidParamNames)

    val caughtException = intercept[Exception] {
      CustomActionValidator.validateCustomActionParams(invalidRequestInvalidValues, testCustomAction)
    }

    caughtException.getMessage shouldBe
      "No such parameter should be defined for this action: " + testCustomAction.name
  }

}
