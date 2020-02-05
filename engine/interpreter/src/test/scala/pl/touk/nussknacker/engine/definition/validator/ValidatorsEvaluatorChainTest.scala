package pl.touk.nussknacker.engine.definition.validator

import java.time.LocalDate

import javax.annotation.Nullable
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.MandatoryValueValidator

class ValidatorsEvaluatorChainTest extends FunSuite with Matchers {

  private val notAnnotatedParam = getFirstParam("notAnnotated", classOf[String])
  private val nullableAnnotatedParam = getFirstParam("nullableAnnotated", classOf[LocalDate])

  private def notAnnotated(param: String) {}

  private def nullableAnnotated(@Nullable nullableParam: LocalDate) {}

  private def getFirstParam(name: String, params: Class[_]*) = {
    this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
  }

  test("evaluate not empty validator by default") {
    ValidatorsEvaluatorChain.evaluate(notAnnotatedParam) shouldBe Some(List(MandatoryValueValidator))
  }

  test("evaluate not empty validator to none when @Nullable annotation detected") {
    ValidatorsEvaluatorChain.evaluate(nullableAnnotatedParam) shouldBe None
  }
}
