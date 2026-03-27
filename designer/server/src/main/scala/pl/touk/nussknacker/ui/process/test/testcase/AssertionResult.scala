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

  def produceFailedComparisonAssertion(operator: String, expected: Any, actual: Any): FailedAssertion =
    ensureNotNullValues(actual, expected).getOrElse {
      val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
      val actualStr   = SpelValuePrettyPrinter.prettyPrintValue(actual)
      FailedAssertion(s"Expected: [$expectedStr] $operator [$actualStr]")
    }

  def produceFailedHasSizeAssertion(expectedSize: Any, actualValue: Any): FailedAssertion =
    ensureNotNullValues(actualValue, expectedSize).getOrElse {
      val actualSize = actualValue match {
        case c: java.util.Collection[_] => c.size()
        case m: java.util.Map[_, _]     => m.size()
        case a: Array[_]                => a.length
        case _                          => -1
      }
      val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expectedSize)
      FailedAssertion(s"Expected size: [$expectedStr] but found: [$actualSize]")
    }

  def produceFailedContainsAssertion(expected: Any, actual: Any): FailedAssertion =
    ensureNotNullValues(actual, expected).getOrElse {
      val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
      val actualStr   = SpelValuePrettyPrinter.prettyPrintValue(actual)
      FailedAssertion(s"Expected [$actualStr] to contain [$expectedStr]")
    }

  def produceFailedMatchesAssertion(pattern: Any, actual: Any): FailedAssertion =
    ensureNotNullValues(actual, pattern).getOrElse {
      val patternStr = SpelValuePrettyPrinter.prettyPrintValue(pattern)
      val actualStr  = SpelValuePrettyPrinter.prettyPrintValue(actual)
      FailedAssertion(s"Expected [$actualStr] to match [$patternStr]")
    }

  private def ensureNotNullValues(actualValue: Any, expectedValue: Any): Option[FailedAssertion] = {
    if (actualValue == null) Some(FailedAssertion("Actual value is null"))
    else if (expectedValue == null) Some(FailedAssertion("Expected value is null"))
    else None
  }

}
