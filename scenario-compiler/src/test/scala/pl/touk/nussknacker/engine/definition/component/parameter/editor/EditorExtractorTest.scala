package pl.touk.nussknacker.engine.definition.component.parameter.editor

import cats.data.NonEmptyList
import com.cronutils.model.Cron
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.JavaSampleEnum
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period}
import java.time.temporal.ChronoUnit
import scala.reflect.ClassTag

class EditorExtractorTest extends AnyFunSuite with Matchers {

  private def notAnnotated(param: String) = ()

  private def dualEditorAnnotated(
      @Editor(
        `type` = EditorType.FIXED_VALUES_EDITOR,
        possibleValues = Array(new LabeledExpression(expression = "'test'", label = "test2"))
      )
      @Editor(`type` = EditorType.SPEL_EDITOR)
      param: String
  ) = ()

  private def dualEditorAnnotatedLazy(
      @Editor(`type` = EditorType.DATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      param: LazyParameter[String]
  ) = ()

  private def editorAnnotated(@Editor(`type` = EditorType.BOOL_EDITOR) param: String) = ()

  private def editorAnnotatedLazy(
      @Editor(`type` = EditorType.BOOL_EDITOR) param: LazyParameter[String]
  ) = ()

  private def rawEditorAnnotated(@Editor(`type` = EditorType.SPEL_EDITOR) param: String) = ()

  private def rawEditorAnnotatedLazy(@Editor(`type` = EditorType.SPEL_EDITOR) param: LazyParameter[String]) = ()

  private def simpleParams(
      javaEnum: JavaSampleEnum,
      localDateTime: LocalDateTime,
      localDate: LocalDate,
      localTime: LocalTime,
      duration: Duration,
      period: Period,
      cron: Cron,
      str: String,
      charseq: CharSequence
  ) = ()

  private def multipleEditorsAnnotated(
      @Editor(
        `type` = EditorType.FIXED_VALUES_EDITOR,
        possibleValues = Array(new LabeledExpression(expression = "'test'", label = "test2"))
      )
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      param: String
  ) = ()

  private val paramNotAnnotated = getFirstParam("notAnnotated", classOf[String])

  private val paramDualEditorAnnotated     = getFirstParam("dualEditorAnnotated", classOf[String])
  private val paramDualEditorLazyAnnotated = getFirstParam("dualEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramEditorAnnotated     = getFirstParam("editorAnnotated", classOf[String])
  private val paramEditorLazyAnnotated = getFirstParam("editorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramRawEditorAnnotated     = getFirstParam("rawEditorAnnotated", classOf[String])
  private val paramRawEditorAnnotatedLazy = getFirstParam("rawEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramMultipleEditorAnnotated = getFirstParam("multipleEditorsAnnotated", classOf[String])

  test("assign RawEditor when no annotation detected") {
    EditorExtractor.extract(
      paramNotAnnotated,
      ParameterConfig.empty,
      GlobalParametersConfig.default
    ) shouldBe NonEmptyList.of(
      SpelParameterEditor,
      SpelTemplateParameterEditor,
    )
  }

  test("detect @Editor annotations") {

    EditorExtractor.extract(paramDualEditorAnnotated, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe
      NonEmptyList.of(
        FixedValuesParameterEditor(
          possibleValues = List(FixedExpressionValue("'test'", "test2"))
        ),
        SpelParameterEditor,
      )

    EditorExtractor.extract(
      paramDualEditorLazyAnnotated,
      ParameterConfig.empty,
      GlobalParametersConfig.default
    ) shouldBe
      NonEmptyList.of(
        DateParameterEditor,
        SpelParameterEditor,
      )
  }

  test("detect @Editor annotation") {

    EditorExtractor.extract(paramEditorAnnotated, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe
      NonEmptyList.of(BoolParameterEditor)

    EditorExtractor.extract(paramEditorLazyAnnotated, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe
      NonEmptyList.of(BoolParameterEditor)
  }

  test("detect Spel @Editor annotation") {
    EditorExtractor.extract(
      paramRawEditorAnnotated,
      ParameterConfig.empty,
      GlobalParametersConfig.default
    ) shouldBe NonEmptyList.of(
      SpelParameterEditor
    )
    EditorExtractor.extract(
      paramRawEditorAnnotatedLazy,
      ParameterConfig.empty,
      GlobalParametersConfig.default
    ) shouldBe NonEmptyList.of(
      SpelParameterEditor
    )
  }

  test("determine editor by config") {
    val fixedValuesEditor = FixedValuesParameterEditor(List(FixedExpressionValue("'expression'", "label")))
    val config            = ParameterConfig(None, Some(List(fixedValuesEditor)), None, None, None, None)

    EditorExtractor.extract(paramNotAnnotated, config, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      fixedValuesEditor
    )
  }

  test("determine editor by type enum") {
    val param = getSimpleParamByType[JavaSampleEnum]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      FixedValuesParameterEditor(
        List(
          FixedExpressionValue(
            s"T(${classOf[JavaSampleEnum].getName}).${JavaSampleEnum.FIRST_VALUE.name()}",
            "first_value"
          ),
          FixedExpressionValue(
            s"T(${classOf[JavaSampleEnum].getName}).${JavaSampleEnum.SECOND_VALUE.name()}",
            "second_value"
          )
        )
      )
    )
  }

  test("determine editor by type LocalDateTime") {
    val param = getSimpleParamByType[LocalDateTime]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      DateTimeParameterEditor
    )
  }

  test("determine editor by type LocalDate") {
    val param = getSimpleParamByType[LocalDate]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      DateParameterEditor
    )
  }

  test("determine editor by type LocalTime") {
    val param = getSimpleParamByType[LocalTime]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      TimeParameterEditor
    )
  }

  test("determine editor by type Duration") {
    val param = getSimpleParamByType[Duration]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES))
    )
  }

  test("determine editor by config for Duration") {
    val param  = getSimpleParamByType[Duration]
    val editor = DurationParameterEditor(timeRangeComponents = List(ChronoUnit.MINUTES))

    EditorExtractor.extract(
      param,
      ParameterConfig.empty.copy(editors = Some(List(editor))),
      GlobalParametersConfig.default
    ) shouldBe NonEmptyList.of(editor)
  }

  test("determine editor by type Period") {
    val param = getSimpleParamByType[Period]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS))
    )
  }

  test("determine editor by type Cron") {
    val param = getSimpleParamByType[Cron]

    EditorExtractor.extract(param, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe NonEmptyList.of(
      CronParameterEditor
    )
  }

  test("add fallback spel editor for lazy parameter") {
    EditorExtractor.extract(
      ParameterData(Typed[LocalDateTime], List.empty, isLazyParameter = true),
      ParameterConfig.empty,
      GlobalParametersConfig.default
    ) shouldBe NonEmptyList.of(DateTimeParameterEditor, SpelParameterEditor)
  }

  test("determine editor by type Charsequence") {
    val charseqParam = getSimpleParamByType[CharSequence]
    val stringParam  = getSimpleParamByType[String]

    val expectedEditor = NonEmptyList.of(
      SpelParameterEditor,
      SpelTemplateParameterEditor,
    )
    EditorExtractor.extract(charseqParam, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe expectedEditor
    EditorExtractor.extract(stringParam, ParameterConfig.empty, GlobalParametersConfig.default) shouldBe expectedEditor
  }

  test("determine multiple editors") {
    EditorExtractor.extract(
      paramMultipleEditorAnnotated,
      ParameterConfig.empty,
      GlobalParametersConfig.default
    ) shouldBe NonEmptyList.of(
      FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test2"))),
      SpelTemplateParameterEditor,
      SpelParameterEditor,
    )
  }

  private def getFirstParam(name: String, params: Class[_]*) = {
    val parameter = this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
    ParameterData(parameter, Typed.typedClass(parameter.getType), isLazyParameter = false)
  }

  private def getSimpleParamByType[T: ClassTag] = {
    val parameter = this.getClass.getDeclaredMethods
      .find(_.getName == "simpleParams")
      .flatMap(_.getParameters.find(_.getType == implicitly[ClassTag[T]].runtimeClass))
      .get
    ParameterData(parameter, Typed.typedClass(parameter.getType), isLazyParameter = false)
  }

}
