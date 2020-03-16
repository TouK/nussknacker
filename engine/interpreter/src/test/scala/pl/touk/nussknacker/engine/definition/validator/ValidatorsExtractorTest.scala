package pl.touk.nussknacker.engine.definition.validator

import java.time.LocalDate
import java.util.Optional

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralIntValidator, MandatoryParameterValidator, NotBlankParameterValidator}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.validation.{Literal, LiteralType}

class ValidatorsExtractorTest extends FunSuite with Matchers {

  private val notAnnotatedParam = getFirstParam("notAnnotated", classOf[String])
  private val nullableAnnotatedParam = getFirstParam("nullableAnnotated", classOf[LocalDate])
  private val optionParam = getFirstParam("optionParam", classOf[Option[String]])
  private val optionalParam = getFirstParam("optionalParam", classOf[Optional[String]])
  private val nullableNotBlankParam = getFirstParam("nullableNotBlankAnnotatedParam", classOf[String])
  private val notBlankParam = getFirstParam("notBlankAnnotatedParam", classOf[String])
  private val literalIntegerParam = getFirstParam("literalIntegerAnnotatedParam", classOf[Int])

  private def notAnnotated(param: String) {}

  private def nullableAnnotated(@Nullable nullableParam: LocalDate) {}

  private def optionParam(stringOption: Option[String]) {}

  private def optionalParam(stringOptional: Optional[String]) {}

  private def nullableNotBlankAnnotatedParam(@Nullable @NotBlank notBlank: String) {}

  private def notBlankAnnotatedParam(@NotBlank notBlank: String) {}

  private def literalIntegerAnnotatedParam(@Literal(`type` = LiteralType.Integer) intParam: Int) {}

  private def getFirstParam(name: String, params: Class[_]*) = {
    this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
  }

  test("extract not empty validator by default") {
    ValidatorsExtractor(None).extract(notAnnotatedParam) shouldBe List(MandatoryParameterValidator)
  }

  test("extract none mandatory value validator when @Nullable annotation detected") {
    ValidatorsExtractor(None).extract(nullableAnnotatedParam) shouldBe List.empty
  }

  test("extract none mandatory value validator when parameter is of type Option") {
    ValidatorsExtractor(None).extract(optionParam) shouldBe List.empty
  }

  test("extract none mandatory value validator when parameter is of type Optional") {
    ValidatorsExtractor(None).extract(optionalParam) shouldBe List.empty
  }

  test("determine fixed values validator when simple fixed value editor passed") {
    val possibleValues = List(FixedExpressionValue("a", "a"))
    ValidatorsExtractor(Some(FixedValuesParameterEditor(possibleValues))).extract(optionalParam)
      .shouldBe(List(FixedValuesValidator(possibleValues)))
  }

  test("determine fixed values validator when dual editor with fixed simple editor passed") {
    val possibleValues = List(FixedExpressionValue("a", "a"))
    ValidatorsExtractor(Some(DualParameterEditor(FixedValuesParameterEditor(possibleValues), DualEditorMode.SIMPLE))).extract(optionalParam)
      .shouldBe(List(FixedValuesValidator(possibleValues)))
  }

  test("extract nullable notBlank value validator when @Nullable @NotBlank annotation detected") {
    ValidatorsExtractor(None).extract(nullableNotBlankParam) shouldBe List(NotBlankParameterValidator)
  }

  test("extract notBlank value validator when @NotBlank annotation detected") {
    ValidatorsExtractor(None).extract(notBlankParam) shouldBe List(MandatoryParameterValidator, NotBlankParameterValidator)
  }

  test("extract literalIntegerParam value validator when @Literal(type=LiteralType.INTEGER) annotation detected") {
    ValidatorsExtractor(None).extract(literalIntegerParam) shouldBe List(MandatoryParameterValidator, LiteralIntValidator)
  }
}
