package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Documentation, HideToString}

import java.util
import scala.jdk.CollectionConverters._

object tests extends TestsFunctions

// This helper should be eventually removed because SpEL comparison logic do apply SpEL implicit conversions
// in contrary to this implementation which uses Scala equality semantics.
trait TestsFunctions extends HideToString {

  @Documentation(description = "Check whether two values are equal")
  def assertEquals(expected: Any, actual: Any): AssertionResult = {
    // we use scala "lenient" equals to allow to compare boxed primitives of different types - like 1L and 1
    if (expected == actual) {
      SuccessfulAssertion
    } else if (checkIfSameElements(expected, actual)) {
      SuccessfulAssertion
    } else {
      AssertionResult.produceFailedEqualsAssertion(expected, actual)
    }
  }

  @Documentation(description = "Check whether two values are not equal")
  def assertNotEquals(expected: Any, actual: Any): AssertionResult = {
    // we use scala "lenient" equals to allow to compare boxed primitives of different types - like 1L and 1
    if (expected == actual) {
      AssertionResult.produceFailedNotEqualsAssertion(expected)
    } else if (checkIfSameElements(expected, actual)) {
      AssertionResult.produceFailedNotEqualsAssertion(expected)
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

}
