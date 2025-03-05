package pl.touk.nussknacker.engine.definition.component.parameter.validator

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor
import pl.touk.nussknacker.engine.definition.component.parameter.{OptionalDeterminer, ParameterData}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor

import java.time.LocalDate
import java.util.Optional
import javax.annotation.Nullable
import javax.validation.constraints.{Max, Min, NotBlank}

class ValidatorsExtractorTest extends AnyFunSuite with Matchers {

  private val notAnnotatedParam      = getFirstParam("notAnnotated", classOf[String])
  private val nullableAnnotatedParam = getFirstParam("nullableAnnotated", classOf[LocalDate])

  private val optionParam   = getFirstParam("optionParam", classOf[Option[String]])
  private val optionalParam = getFirstParam("optionalParam", classOf[Optional[String]])

  private val nullableNotBlankParam = getFirstParam("nullableNotBlankAnnotatedParam", classOf[String])
  private val notBlankParam         = getFirstParam("notBlankAnnotatedParam", classOf[String])

  private val compileTimeEvaluableIntParam = getFirstParam("compileTimeEvaluableIntAnnotatedParam", classOf[Int])
  private val compileTimeEvaluableIntegerParam =
    getFirstParam("compileTimeEvaluableIntegerAnnotatedParam", classOf[Integer])
  private val compileTimeEvaluableNullableIntegerParam =
    getFirstParam("compileTimeEvaluableNullableIntegerAnnotatedParam", classOf[Integer])
  private val compileTimeEvaluableStringParam =
    getFirstParam("compileTimeEvaluableStringAnnotatedParam", classOf[String])

  private val minimalValueIntegerParam    = getFirstParam("minimalValueIntegerAnnotatedParam", classOf[Int])
  private val minimalValueBigDecimalParam = getFirstParam("minimalValueBigDecimalAnnotatedParam", classOf[BigDecimal])

  private val maximalValueIntegerParam    = getFirstParam("maximalValueIntegerAnnotatedParam", classOf[Int])
  private val maximalValueBigDecimalParam = getFirstParam("maximalValueBigDecimalAnnotatedParam", classOf[BigDecimal])

  private val minimalAndMaximalValueIntegerParam =
    getFirstParam("minimalAndMaximalValueIntegerAnnotatedParam", classOf[Int])
  private val minimalAndMaximalValueBigDecimalParam =
    getFirstParam("minimalAndMaximalValueBigDecimalAnnotatedParam", classOf[BigDecimal])

  private def notAnnotated(param: String) = ()

  private def nullableAnnotated(@Nullable nullableParam: LocalDate) = ()

  private def optionParam(stringOption: Option[String]) = ()

  private def optionalParam(stringOptional: Optional[String]) = ()

  private def nullableNotBlankAnnotatedParam(@Nullable @NotBlank notBlank: String) = ()

  private def notBlankAnnotatedParam(@NotBlank notBlank: String) = ()

  private def compileTimeEvaluableIntAnnotatedParam(@CompileTimeEvaluableValue intParam: Int) = ()

  private def compileTimeEvaluableIntegerAnnotatedParam(@CompileTimeEvaluableValue integerParam: Integer) = ()

  private def compileTimeEvaluableNullableIntegerAnnotatedParam(
      @Nullable @CompileTimeEvaluableValue integerParam: Integer
  ) = ()

  private def compileTimeEvaluableStringAnnotatedParam(@CompileTimeEvaluableValue stringParam: String) = ()

  private def minimalValueIntegerAnnotatedParam(@Min(value = 0) minimalValue: Int) = ()

  private def minimalValueBigDecimalAnnotatedParam(@Min(value = 0) minimalValue: BigDecimal) = ()

  private def maximalValueIntegerAnnotatedParam(@Max(value = 0) maximalValue: Int) = ()

  private def maximalValueBigDecimalAnnotatedParam(@Max(value = 0) maximalValue: BigDecimal) = ()

  private def minimalAndMaximalValueIntegerAnnotatedParam(@Min(value = 0) @Max(value = 1) value: Int) = ()

  private def minimalAndMaximalValueBigDecimalAnnotatedParam(@Min(value = 0) @Max(value = 1) value: BigDecimal) = ()

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
    ValidatorsExtractor
      .extract(
        validatorParams(
          optionalParam,
          ParameterConfig.empty.copy(editor = Some(FixedValuesParameterEditor(possibleValues)))
        )
      )
      .shouldBe(List(FixedValuesValidator(possibleValues)))
  }

  test("not determine fixed values validator when dual editor was passed") {
    val possibleValues = List(FixedExpressionValue("a", "a"))
    ValidatorsExtractor
      .extract(
        validatorParams(
          optionalParam,
          ParameterConfig.empty
            .copy(editor = Some(DualParameterEditor(FixedValuesParameterEditor(possibleValues), DualEditorMode.SIMPLE)))
        )
      )
      .shouldBe(empty)
  }

  test("extract nullable notBlank value validator when @Nullable @NotBlank annotation detected") {
    ValidatorsExtractor.extract(validatorParams(nullableNotBlankParam)) shouldBe List(NotBlankParameterValidator)
  }

  test("extract notBlank value validator when @NotBlank annotation detected") {
    ValidatorsExtractor.extract(validatorParams(notBlankParam)) shouldBe List(
      MandatoryParameterValidator,
      NotBlankParameterValidator
    )
  }

  test("extract compileTimeEvaluableIntParam value validator when @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(compileTimeEvaluableIntParam)) shouldBe List(
      MandatoryParameterValidator,
      CompileTimeEvaluableValueValidator
    )
  }

  test("extract compileTimeEvaluableIntegerParam value validator when @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(compileTimeEvaluableIntegerParam)) shouldBe List(
      MandatoryParameterValidator,
      CompileTimeEvaluableValueValidator
    )
  }

  test("extract compileTimeEvaluableOptionalIntegerParam value validator when @Nullable @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(compileTimeEvaluableNullableIntegerParam)) shouldBe List(
      CompileTimeEvaluableValueValidator
    )
  }

  test("should extract compileTimeEvaluableStringParam value validator when @Literal annotation detected") {
    ValidatorsExtractor.extract(validatorParams(compileTimeEvaluableStringParam)) shouldBe List(
      MandatoryParameterValidator,
      CompileTimeEvaluableValueValidator
    )
  }

  test("extract minimalValueIntegerParam value validator when @Min annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalValueIntegerParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0))
  }

  test("extract minimalValueBigDecimalParam value validator when @Min annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalValueBigDecimalParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0))
  }

  test("extract maximalValueIntegerParam value validator when @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(maximalValueIntegerParam)) shouldBe
      List(MandatoryParameterValidator, MaximalNumberValidator(0))
  }

  test("extract maximalValueBigDecimalParam value validator when @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(maximalValueBigDecimalParam)) shouldBe
      List(MandatoryParameterValidator, MaximalNumberValidator(0))
  }

  test("extract minimalAndMaximalValueIntegerParam value validator when @Min and @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalAndMaximalValueIntegerParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0), MaximalNumberValidator(1))
  }

  test("extract minimalAndMaximalValueBigDecimalParam value validator when @Min and @Max annotation detected") {
    ValidatorsExtractor.extract(validatorParams(minimalAndMaximalValueBigDecimalParam)) shouldBe
      List(MandatoryParameterValidator, MinimalNumberValidator(0), MaximalNumberValidator(1))
  }

  test("determine validators based on config") {
    val config = ParameterConfig(None, None, Some(List(NotBlankParameterValidator)), None, None)

    ValidatorsExtractor.extract(validatorParams(notAnnotatedParam, parameterConfig = config)) shouldBe
      List(MandatoryParameterValidator, NotBlankParameterValidator)
  }

  private def validatorParams(
      rawJavaParam: java.lang.reflect.Parameter,
      parameterConfig: ParameterConfig = ParameterConfig.empty
  ) = {
    val parameterData   = ParameterData(rawJavaParam, ClassDefinitionExtractor.extractParameterType(rawJavaParam))
    val extractedEditor = EditorExtractor.extract(parameterData, parameterConfig)
    ValidatorExtractorParameters(
      parameterData,
      OptionalDeterminer.isOptional(
        parameterData,
        classOf[Optional[_]].isAssignableFrom(rawJavaParam.getType),
        classOf[Option[_]].isAssignableFrom(rawJavaParam.getType)
      ),
      parameterConfig,
      extractedEditor
    )
  }

}
