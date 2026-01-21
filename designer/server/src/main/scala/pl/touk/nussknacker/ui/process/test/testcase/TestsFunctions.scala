package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

object tests extends TestsFunctions

trait TestsFunctions extends HideToString {

  @Documentation(description = "Check whether two values are equal")
  def assertEquals(@ParamName("expected") expected: Any, @ParamName("actual") actual: Any): AssertionResult = {
    AssertionResult.assertEquals(expected, actual)
  }

  @Documentation(description = "Check whether two values are not equal")
  def assertNotEquals(@ParamName("expected") expected: Any, @ParamName("actual") actual: Any): AssertionResult = {
    AssertionResult.assertNotEquals(expected, actual)
  }

}
