package pl.touk.nussknacker.engine.definition.validator

import java.time.LocalDate
import java.util.Optional

import javax.annotation.Nullable
import javax.validation.constraints.{Max, Min, NotBlank}
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

  private val minimalValueIntegerWithDefaultAnnotationMessageParam = getFirstParam("minimalValueIntegerWithDefaultAnnotationMessageAnnotatedParam", classOf[Int])
  private val minimalValueBigDecimalWithDefaultAnnotationMessageParam = getFirstParam("minimalValueBigDecimalWithDefaultAnnotationMessageAnnotatedParam", classOf[BigDecimal])
  private val minimalValueIntegerWithMessageParam = getFirstParam("minimalValueIntegerWithMessageAnnotatedParam", classOf[Int])
  private val minimalValueBigDecimalWithMessageParam = getFirstParam("minimalValueBigDecimalWithMessageAnnotatedParam", classOf[BigDecimal])

  private val maximalValueIntegerWithDefaultAnnotationMessageParam = getFirstParam("maximalValueIntegerWithDefaultAnnotationMessageAnnotatedParam", classOf[Int])
  private val maximalValueBigDecimalWithDefaultAnnotationMessageParam = getFirstParam("maximalValueBigDecimalWithDefaultAnnotationMessageAnnotatedParam", classOf[BigDecimal])
  private val maximalValueIntegerWithMessageParam = getFirstParam("maximalValueIntegerWithMessageAnnotatedParam", classOf[Int])
  private val maximalValueBigDecimalWithMessageParam = getFirstParam("maximalValueBigDecimalWithMessageAnnotatedParam", classOf[BigDecimal])

  private val minimalNumberValidatorDefaultAnnotationMessage: String = "{javax.validation.constraints.Min.message}"
  private val maximalNumberValidatorDefaultAnnotationMessage: String = "{javax.validation.constraints.Max.message}"

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


  private def minimalValueIntegerWithDefaultAnnotationMessageAnnotatedParam(@Min(value = 0) minimalValue: Int) {}

  private def minimalValueBigDecimalWithDefaultAnnotationMessageAnnotatedParam(@Min(value = 0) minimalValue: BigDecimal) {}

  private def minimalValueIntegerWithMessageAnnotatedParam(@Min(value = 0, message = "test") minimalValue: Int) {}

  private def minimalValueBigDecimalWithMessageAnnotatedParam(@Min(value = 0, message = "test") minimalValue: BigDecimal) {}


  private def maximalValueIntegerWithDefaultAnnotationMessageAnnotatedParam(@Max(value = 0) maximalValue: Int) {}

  private def maximalValueBigDecimalWithDefaultAnnotationMessageAnnotatedParam(@Max(value = 0) maximalValue: BigDecimal) {}

  private def maximalValueIntegerWithMessageAnnotatedParam(@Max(value = 0, message = "test") maximalValue: Int) {}

  private def maximalValueBigDecimalWithMessageAnnotatedParam(@Max(value = 0, message = "test") maximalValue: BigDecimal) {}


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


  test("extract minimalValueIntegerWithDefaultAnnotationMessageParam value validator when @Min annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalValueIntegerWithDefaultAnnotationMessageParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0, minimalNumberValidatorDefaultAnnotationMessage))
  }

  test("extract minimalValueBigDecimalWithDefaultAnnotationMessageParam value validator when @Min annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalValueBigDecimalWithDefaultAnnotationMessageParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0, minimalNumberValidatorDefaultAnnotationMessage))
  }

  test("extract minimalValueIntegerWithMessageParam value validator when @Min annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalValueIntegerWithMessageParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0, "test"))
  }

  test("extract minimalValueBigDecimalWithMessageParam value validator when @Min annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalValueBigDecimalWithMessageParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0, "test"))
  }


  test("extract maximalValueIntegerWithDefaultAnnotationMessageParam value validator when @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(maximalValueIntegerWithDefaultAnnotationMessageParam)) shouldBe
      List(MandatoryParameterValidator, MaximalNumberValidator(0, maximalNumberValidatorDefaultAnnotationMessage))
  }

  test("extract maximalValueBigDecimalWithDefaultAnnotationMessageParam value validator when @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(maximalValueBigDecimalWithDefaultAnnotationMessageParam)) shouldBe
      List(MandatoryParameterValidator, MaximalNumberValidator(0, maximalNumberValidatorDefaultAnnotationMessage))
  }

  test("extract maximalValueIntegerWithMessageParam value validator when @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(maximalValueIntegerWithMessageParam)) shouldBe
      List(MandatoryParameterValidator, MaximalNumberValidator(0, "test"))
  }

  test("extract maximalValueBigDecimalWithMessageParam value validator when @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(maximalValueBigDecimalWithMessageParam)) shouldBe
      List(MandatoryParameterValidator, MaximalNumberValidator(0, "test"))
  }

  private def validatorParams(rawJavaParam: java.lang.reflect.Parameter,
                              editor: Option[ParameterEditor] = None) =
    ValidatorExtractorParameters(rawJavaParam, EspTypeUtils.extractParameterType(rawJavaParam),
      classOf[Option[_]].isAssignableFrom(rawJavaParam.getType), classOf[Optional[_]].isAssignableFrom(rawJavaParam.getType), editor)

}