package pl.touk.nussknacker.ui.process.test.testcase

import java.util
import scala.jdk.CollectionConverters._

sealed trait AssertionResult

case object SuccessfulAssertion extends AssertionResult

case class FailedAssertion(message: String) extends AssertionResult

object AssertionResult {

  def assertEquals(expected: Any, actual: Any): AssertionResult = {
    // we use scala "lenient" equals to allow to compare boxed primitives of different types - like 1L and 1
    if (expected == actual) {
      SuccessfulAssertion
    } else if (checkIfSameElements(expected, actual)) {
      SuccessfulAssertion
    } else {
      produceFailedAssertion(expected, actual)
    }
  }

  def assertNotEquals(expected: Any, actual: Any): AssertionResult = {
    // we use scala "lenient" equals to allow to compare boxed primitives of different types - like 1L and 1
    if (expected == actual) {
      produceFailedNotEqualsAssertion(expected)
    } else if (checkIfSameElements(expected, actual)) {
      produceFailedNotEqualsAssertion(expected)
    } else {
      SuccessfulAssertion
    }
  }

  // todo: should it work recursively - e.g for arrays nested in lists?
  private def checkIfSameElements(expected: Any, actual: Any) = {
    if ((expected.isInstanceOf[Array[_]] || expected.isInstanceOf[util.Collection[_]]) &&
      (actual.isInstanceOf[Array[_]] || actual.isInstanceOf[util.Collection[_]])) {
      convertToSeq(expected) == convertToSeq(actual)
    } else {
      false
    }
  }

  private def convertToSeq(value: Any): Seq[_] = {
    value match {
      case a: Array[_]           => a.toSeq
      case c: util.Collection[_] => c.asScala.toSeq
    }
  }

  private def produceFailedAssertion(expected: Any, actual: Any) = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    val actualStr   = SpelValuePrettyPrinter.prettyPrintValue(actual)
    FailedAssertion(s"Expected: [$expectedStr] but found [$actualStr]")
  }

  private def produceFailedNotEqualsAssertion(expected: Any) = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    FailedAssertion(s"Expected value different from: [$expectedStr]")
  }

}
