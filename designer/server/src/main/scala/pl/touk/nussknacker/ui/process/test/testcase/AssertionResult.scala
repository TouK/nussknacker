package pl.touk.nussknacker.ui.process.test.testcase

sealed trait AssertionResult

case object SuccessfulAssertion extends AssertionResult

case class FailedAssertion(message: String) extends AssertionResult

object AssertionResult {

  def produceFailedEqualsAssertion(expected: Any, actual: Any): FailedAssertion = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    val actualStr   = SpelValuePrettyPrinter.prettyPrintValue(actual)
    FailedAssertion(s"Expected: [$expectedStr] but found [$actualStr]")
  }

  def produceFailedNotEqualsAssertion(expected: Any): FailedAssertion = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    FailedAssertion(s"Expected value different from: [$expectedStr]")
  }

  def produceFailedHasSizeAssertion(expectedSize: Any, actualSize: Int): FailedAssertion = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expectedSize)
    FailedAssertion(s"Expected size: [$expectedStr] but found: [$actualSize]")
  }

  def produceFailedComparisonAssertion(operator: String, expected: Any, actual: Any): FailedAssertion = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    val actualStr   = SpelValuePrettyPrinter.prettyPrintValue(actual)
    FailedAssertion(s"Expected: [$expectedStr] $operator [$actualStr]")
  }

}
