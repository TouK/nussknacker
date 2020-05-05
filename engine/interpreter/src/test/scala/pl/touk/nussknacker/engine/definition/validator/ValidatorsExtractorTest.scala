package pl.touk.nussknacker.engine.definition.validator

import java.time.LocalDate
import java.util.Optional

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.validation.Literal
import pl.touk.nussknacker.engine.types.EspTypeUtils

class ValidatorsExtractorTest extends FunSuite with Matchers {

  private val notAnnotatedParam = getFirstParam("notAnnotated", classOf[String])
  private val nullableAnnotatedParam = getFirstParam("nullableAnnotated", classOf[LocalDate])
  private val optionParam = getFirstParam("optionParam", classOf[Option[String]])
  private val optionalParam = getFirstParam("optionalParam", classOf[Optional[String]])
  private val nullableNotBlankParam = getFirstParam("nullableNotBlankAnnotatedParam", classOf[String])
  private val notBlankParam = getFirstParam("notBlankAnnotatedParam", classOf[String])
  private val literalIntParam = getFirstParam("literalIntAnnotatedParam", classOf[Int])
  private val literalIntegerParam = getFirstParam("literalIntegerAnnotatedParam", classOf[Integer])
  private val literalNullableIntegerParam = getFirstParam("literalNullableIntegerAnnotatedParam", classOf[Integer])
  private val literalStringParam = getFirstParam("literalStringAnnotatedParam", classOf[String])

  private def notAnnotated(param: String) {}

  private def nullableAnnotated(@Nullable nullableParam: LocalDate) {}

  private def optionParam(stringOption: Option[String]) {}

  private def optionalParam(stringOptional: Optional[String]) {}

  private def nullableNotBlankAnnotatedParam(@Nullable @NotBlank notBlank: String) {}

  private def notBlankAnnotatedParam(@NotBlank notBlank: String) {}

  private def literalIntAnnotatedParam(@Literal intParam: Int) {}

  private def literalIntegerAnnotatedParam(@Literal integerParam: Integer) {}

  private def literalNullableIntegerAnnotatedParam(@Nullable @Literal integerParam: Integer) {}

  private def literalStringAnnotatedParam(@Literal stringParam: String) {}

  private def getFirstParam(name: String, params: Class[_]*) = {
    this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
  }

  test("extract not empty validator by default") {
    ValidatorsExtractor.extract(validatorParams(notAnnotatedParam)) shouldBe List(MandatoryParameterValidator)
  }

  test("extract none mandatory value validator when @Nullable annotation detected") {
    ValidatorsExtractor.extract(validatorParams(nullableAnnotatedParam)) shouldBe List.empty
  }

  test("extract none mandatory value validator when parameter is of type Option") {
    ValidatorsExtractor.extract(validatorParams(optionParam)) shouldBe List.empty
  }

  test("extract none mandatory value validator when parameter is of type Optional") {
    ValidatorsExtractor.extract(validatorParams(optionalParam)) shouldBe List.empty
  }

  test("determine fixed values validator when simple fixed value editor passed") {
    val possibleValues = List(FixedExpressionValue("a", "a"))
    ValidatorsExtractor.extract(validatorParams(optionalParam, Some(FixedValuesParameterEditor(possibleValues))))
      .shouldBe(List(FixedValuesValidator(possibleValues)))
  }

  test("not determine fixed values validator when dual editor was passed") {
    val possibleValues = List(FixedExpressionValue("a", "a"))
    ValidatorsExtractor.extract(validatorParams(optionalParam, Some(DualParameterEditor(FixedValuesParameterEditor(possibleValues), DualEditorMode.SIMPLE))))
      .shouldBe(empty)
  }

  test("extract nullable notBlank value validator when @Nullable @NotBlank annotation detected") {
    ValidatorsExtractor.extract(validatorParams(nullableNotBlankParam)) shouldBe List(NotBlankParameterValidator)
  }

  test("extract notBlank value validator when @NotBlank annotation detected") {
    ValidatorsExtractor.extract(validatorParams(notBlankParam)) shouldBe List(MandatoryParameterValidator, NotBlankParameterValidator)
  }

  test("extract literalIntParam value validator when @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(literalIntParam)) shouldBe List(MandatoryParameterValidator, LiteralParameterValidator.integerValidator)
  }

  test("extract literalIntegerParam value validator when @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(literalIntegerParam)) shouldBe List(MandatoryParameterValidator, LiteralParameterValidator.integerValidator)
  }

  test("extract literalOptionalIntegerParam value validator when @Nullable @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(literalNullableIntegerParam)) shouldBe List(LiteralParameterValidator.integerValidator)
  }

  test("should not extract literalStringParam value validator when @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(literalStringParam)) shouldBe List(MandatoryParameterValidator)
  }

  private def validatorParams(rawJavaParam: java.lang.reflect.Parameter,
                              editor: Option[ParameterEditor] = None) =
    ValidatorExtractorParameters(rawJavaParam, EspTypeUtils.extractParameterType(rawJavaParam),
      classOf[Option[_]].isAssignableFrom(rawJavaParam.getType), classOf[Optional[_]].isAssignableFrom(rawJavaParam.getType), editor)

}
