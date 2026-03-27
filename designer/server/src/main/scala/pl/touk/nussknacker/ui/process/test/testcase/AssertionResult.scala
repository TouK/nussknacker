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
      val actualSizeOpt = actualValue match {
        case c: java.util.Collection[_] => Some(c.size())
        case m: java.util.Map[_, _]     => Some(m.size())
        case a: Array[_]                => Some(a.length)
        case _                          => None
      }
      actualSizeOpt match {
        case Some(actualSize) =>
          val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expectedSize)
          FailedAssertion(s"Expected size: [$expectedStr] but found: [$actualSize]")
        case None =>
          val actualStr  = SpelValuePrettyPrinter.prettyPrintValue(actualValue)
          val actualType = actualValue.getClass.getSimpleName
          FailedAssertion(s"Cannot check size of [$actualStr] (type: $actualType)")
      }
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
