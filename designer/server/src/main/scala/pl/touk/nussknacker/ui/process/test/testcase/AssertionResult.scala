package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

import java.util
import scala.jdk.CollectionConverters._

sealed trait AssertionResult

case object SuccessfulAssertion extends AssertionResult

case class FailedAssertion(message: String) extends AssertionResult

object tests extends TestsFunctions

trait TestsFunctions extends HideToString {

  @Documentation(description = "Check whether two values are equals")
  def assertEquals(@ParamName("expected") expected: Any, @ParamName("actual") actual: Any): AssertionResult = {
    // we use scala "lenient" equals to allow to compare boxed primitives of different types - like 1L and 1
    if (expected == actual) {
      SuccessfulAssertion
    } else if (checkIfSameElements(expected, actual)) {
      SuccessfulAssertion
    } else {
      produceFailedAssertion(expected, actual)
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

}
