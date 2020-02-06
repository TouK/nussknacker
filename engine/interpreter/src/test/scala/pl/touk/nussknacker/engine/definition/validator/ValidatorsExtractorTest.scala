package pl.touk.nussknacker.engine.definition.validator

import java.time.LocalDate
import java.util.Optional

import javax.annotation.Nullable
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.MandatoryValueValidator

class ValidatorsExtractorTest extends FunSuite with Matchers {

  private val notAnnotatedParam = getFirstParam("notAnnotated", classOf[String])
  private val nullableAnnotatedParam = getFirstParam("nullableAnnotated", classOf[LocalDate])
  private val optionParam = getFirstParam("optionParam", classOf[Option[String]])
  private val optionalParam = getFirstParam("optionalParam", classOf[Optional[String]])

  private def notAnnotated(param: String) {}

  private def nullableAnnotated(@Nullable nullableParam: LocalDate) {}

  private def optionParam(stringOption: Option[String]) {}

  private def optionalParam(stringOptional: Optional[String]) {}

  private def getFirstParam(name: String, params: Class[_]*) = {
    this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
  }

  test("extract not empty validator by default") {
    ValidatorsExtractor.extract(notAnnotatedParam) shouldBe List(MandatoryValueValidator)
  }

  test("extract none mandatory value validator when @Nullable annotation detected") {
    ValidatorsExtractor.extract(nullableAnnotatedParam) shouldBe List.empty
  }

  test("extract none mandatory value validator when parameter is of type Option") {
    ValidatorsExtractor.extract(optionParam) shouldBe List.empty
  }

  test("extract none mandatory value validator when parameter is of type Optional") {
    ValidatorsExtractor.extract(optionalParam) shouldBe List.empty
  }
}
