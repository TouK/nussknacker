package pl.touk.nussknacker.engine.definition.component.parameter.validator

import cats.data.Validated
import cats.data.Validated.valid
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.{
  CompileTimeEvaluableValue,
  CustomValidator,
  NonNegativeDuration,
  PositiveDuration,
  ValidatorMode
}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor
import pl.touk.nussknacker.engine.definition.component.parameter.{OptionalDeterminer, ParameterData}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.time.{Duration, LocalDate}
import java.util.Optional
import javax.annotation.Nullable
import javax.validation.constraints.{Max, Min, NotBlank}

class SampleCustomValidator extends CustomCompileTimeParameterValidator {
  override val name: String = "sampleCustomValidator"

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = valid(())

}

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

  private val nonNegativeDurationParam = getFirstParam("nonNegativeDurationAnnotatedParam", classOf[Duration])
  private val positiveDurationParam    = getFirstParam("positiveDurationAnnotatedParam", classOf[Duration])

  private val compileTimeNonNegativeDurationParam =
    getFirstParam("compileTimeNonNegativeDurationAnnotatedParam", classOf[Duration])
  private val compileTimePositiveDurationParam =
    getFirstParam("compileTimePositiveDurationAnnotatedParam", classOf[Duration])
  private val fullNonNegativeDurationParam = getFirstParam("fullNonNegativeDurationAnnotatedParam", classOf[Duration])
  private val fullPositiveDurationParam    = getFirstParam("fullPositiveDurationAnnotatedParam", classOf[Duration])

  private val customValidatorParam = getFirstParam("customValidatorAnnotatedParam", classOf[String])

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

  private def nonNegativeDurationAnnotatedParam(@NonNegativeDuration nonNegativeDuration: Duration) = ()

  private def positiveDurationAnnotatedParam(@PositiveDuration positiveDuration: Duration) = ()

  private def compileTimeNonNegativeDurationAnnotatedParam(
      @NonNegativeDuration(mode = ValidatorMode.COMPILE_TIME) nonNegativeDuration: Duration
  ) = ()

  private def compileTimePositiveDurationAnnotatedParam(
      @PositiveDuration(mode = ValidatorMode.COMPILE_TIME) positiveDuration: Duration
  ) = ()

  private def fullNonNegativeDurationAnnotatedParam(
      @NonNegativeDuration(mode = ValidatorMode.COMPILE_TIME_AND_RUNTIME) nonNegativeDuration: Duration
  ) = ()

  private def fullPositiveDurationAnnotatedParam(
      @PositiveDuration(mode = ValidatorMode.COMPILE_TIME_AND_RUNTIME) positiveDuration: Duration
  ) = ()

  private def customValidatorAnnotatedParam(@CustomValidator(classOf[SampleCustomValidator]) param: String) = ()

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
          ParameterConfig.empty.copy(editors = Some(List(FixedValuesParameterEditor(possibleValues))))
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
            .copy(editors =
              Some(
                List(
                  FixedValuesParameterEditor(possibleValues),
                  SpelParameterEditor,
                )
              )
            )
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

  test(
    "extract compile-time nonNegativeDuration validator when @NonNegativeDuration (AUTO) detected on an eager param"
  ) {
    ValidatorsExtractor.extract(validatorParams(nonNegativeDurationParam)) shouldBe
      List(MandatoryParameterValidator, CompileTimeNonNegativeDurationValidator)
  }

  test("extract compile-time positiveDuration validator when @PositiveDuration (AUTO) detected on an eager param") {
    ValidatorsExtractor.extract(validatorParams(positiveDurationParam)) shouldBe
      List(MandatoryParameterValidator, CompileTimePositiveDurationValidator)
  }

  test("extract full nonNegativeDuration validator when @NonNegativeDuration (AUTO) detected on a lazy param") {
    ValidatorsExtractor.extract(validatorParams(nonNegativeDurationParam, isLazyParameter = true)) shouldBe
      List(MandatoryParameterValidator, NonNegativeDurationValidator)
  }

  test("extract full positiveDuration validator when @PositiveDuration (AUTO) detected on a lazy param") {
    ValidatorsExtractor.extract(validatorParams(positiveDurationParam, isLazyParameter = true)) shouldBe
      List(MandatoryParameterValidator, PositiveDurationValidator)
  }

  test(
    "extract compile-time nonNegativeDuration validator when @NonNegativeDuration(COMPILE_TIME) forces it on a lazy param"
  ) {
    ValidatorsExtractor.extract(validatorParams(compileTimeNonNegativeDurationParam, isLazyParameter = true)) shouldBe
      List(MandatoryParameterValidator, CompileTimeNonNegativeDurationValidator)
  }

  test(
    "extract compile-time positiveDuration validator when @PositiveDuration(COMPILE_TIME) forces it on a lazy param"
  ) {
    ValidatorsExtractor.extract(validatorParams(compileTimePositiveDurationParam, isLazyParameter = true)) shouldBe
      List(MandatoryParameterValidator, CompileTimePositiveDurationValidator)
  }

  test(
    "extract full nonNegativeDuration validator when @NonNegativeDuration(COMPILE_TIME_AND_RUNTIME) forces it on an eager param"
  ) {
    ValidatorsExtractor.extract(validatorParams(fullNonNegativeDurationParam)) shouldBe
      List(MandatoryParameterValidator, NonNegativeDurationValidator)
  }

  test(
    "extract full positiveDuration validator when @PositiveDuration(COMPILE_TIME_AND_RUNTIME) forces it on an eager param"
  ) {
    ValidatorsExtractor.extract(validatorParams(fullPositiveDurationParam)) shouldBe
      List(MandatoryParameterValidator, PositiveDurationValidator)
  }

  test("throw with the list of supported modes when the resolved mode has no validator") {
    val ex = intercept[IllegalArgumentException] {
      ValidatorsExtractor.validatorForMode(
        validatorParams(positiveDurationParam),
        classOf[PositiveDuration],
        ValidatorMode.COMPILE_TIME_AND_RUNTIME,
        Map(ValidatorMode.COMPILE_TIME -> CompileTimePositiveDurationValidator)
      )
    }
    ex.getMessage shouldBe
      "@PositiveDuration does not support the COMPILE_TIME_AND_RUNTIME mode. Supported modes: COMPILE_TIME."
  }

  test("extract custom validator when @CustomValidator annotation detected") {
    val extracted = ValidatorsExtractor.extract(validatorParams(customValidatorParam))
    extracted should have size 2
    extracted.head shouldBe MandatoryParameterValidator
    val underlyingCustomValidator = extracted(1).asInstanceOf[CustomParameterValidatorByClassLoader].resolved.underlying
    underlyingCustomValidator shouldBe a[SampleCustomValidator]
  }

  test("determine validators based on config") {
    val config = ParameterConfig(None, None, Some(List(NotBlankParameterValidator)), None, None, None)

    ValidatorsExtractor.extract(validatorParams(notAnnotatedParam, parameterConfig = config)) shouldBe
      List(MandatoryParameterValidator, NotBlankParameterValidator)
  }

  private def validatorParams(
      rawJavaParam: java.lang.reflect.Parameter,
      parameterConfig: ParameterConfig = ParameterConfig.empty,
      globalParametersConfig: GlobalParametersConfig = GlobalParametersConfig.default,
      isLazyParameter: Boolean = false
  ) = {
    val parameterData =
      ParameterData(rawJavaParam, ClassDefinitionExtractor.extractParameterType(rawJavaParam), isLazyParameter)
    val extractedEditor = EditorExtractor.extract(parameterData, parameterConfig, globalParametersConfig)
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
