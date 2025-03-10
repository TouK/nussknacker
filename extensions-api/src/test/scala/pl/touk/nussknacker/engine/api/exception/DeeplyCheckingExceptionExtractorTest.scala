package pl.touk.nussknacker.engine.api.exception

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class DeeplyCheckingExceptionExtractorTest extends AnyFunSuiteLike with Matchers with OptionValues {

  test("should check if given exception matches") {
    val matchingException = new IllegalArgumentException("foo")
    DeeplyCheckingExceptionExtractor
      .forClass[RuntimeException]
      .unapply(matchingException)
      .value shouldBe matchingException
    DeeplyCheckingExceptionExtractor
      .forClass[RuntimeException]
      .unapply(new Exception("other exception")) shouldBe empty
  }

  test("should check if exception exception cause matches") {
    val matchingCause = new IllegalArgumentException("foo")
    DeeplyCheckingExceptionExtractor
      .forClass[RuntimeException]
      .unapply(new Exception("some wrapper exception", matchingCause))
      .value shouldBe matchingCause
  }

}
