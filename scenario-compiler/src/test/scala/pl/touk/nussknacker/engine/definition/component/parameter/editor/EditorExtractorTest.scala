package pl.touk.nussknacker.engine.definition.component.parameter.editor

import cats.implicits.catsSyntaxOptionId
import com.cronutils.model.Cron
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
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
      @ParameterEditor(
        `type` = ParameterEditorType.FIXED_VALUES_EDITOR,
        possibleValues = Array(new LabeledExpression(expression = "'test'", label = "test2")),
      )
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      param: String
  ) = ()

  private def dualEditorAnnotatedLazy(
      @ParameterEditor(`type` = ParameterEditorType.DATE_EDITOR)
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      param: LazyParameter[String]
  ) = ()

  private def simpleEditorAnnotated(@ParameterEditor(`type` = ParameterEditorType.BOOL_EDITOR) param: String) = ()

  private def simpleEditorAnnotatedLazy(
      @ParameterEditor(`type` = ParameterEditorType.BOOL_EDITOR) param: LazyParameter[String]
  ) = ()

  private def rawEditorAnnotated(@ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR) param: String) = ()

  private def rawEditorAnnotatedLazy(
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR) param: LazyParameter[String]
  ) = ()

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

  private val paramNotAnnotated = getFirstParam("notAnnotated", classOf[String])

  private val paramDualEditorAnnotated     = getFirstParam("dualEditorAnnotated", classOf[String])
  private val paramDualEditorLazyAnnotated = getFirstParam("dualEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramSimpleEditorAnnotated = getFirstParam("simpleEditorAnnotated", classOf[String])
  private val paramSimpleEditorLazyAnnotated =
    getFirstParam("simpleEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramRawEditorAnnotated     = getFirstParam("rawEditorAnnotated", classOf[String])
  private val paramRawEditorAnnotatedLazy = getFirstParam("rawEditorAnnotatedLazy", classOf[LazyParameter[String]])

  test("assign RawEditor when no annotation detected") {
    EditorExtractor.extract(paramNotAnnotated, ParameterConfig.empty) shouldBe ParameterEditors(
      SpelParameterEditor,
      SpelTemplateParameterEditor,
    ).some
  }

  test("detect either @SimpleEditor and @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR) annotations") {

    EditorExtractor.extract(paramDualEditorAnnotated, ParameterConfig.empty) shouldBe
      ParameterEditors(
        FixedValuesParameterEditor(
          possibleValues = List(FixedExpressionValue("'test'", "test2"))
        ),
        SpelParameterEditor,
      ).some

    EditorExtractor.extract(paramDualEditorLazyAnnotated, ParameterConfig.empty) shouldBe
      ParameterEditors(
        DateParameterEditor,
        SpelParameterEditor,
      ).some
  }

  test("detect @SimpleEditor annotation") {

    EditorExtractor.extract(paramSimpleEditorAnnotated, ParameterConfig.empty) shouldBe
      ParameterEditors(BoolParameterEditor).some

    EditorExtractor.extract(paramSimpleEditorLazyAnnotated, ParameterConfig.empty) shouldBe
      ParameterEditors(BoolParameterEditor).some
  }

  test("detect @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR) annotation") {
    EditorExtractor.extract(paramRawEditorAnnotated, ParameterConfig.empty) shouldBe ParameterEditors(
      SpelParameterEditor
    ).some
    EditorExtractor.extract(paramRawEditorAnnotatedLazy, ParameterConfig.empty) shouldBe ParameterEditors(
      SpelParameterEditor
    ).some
  }

  test("determine editor by config") {
    val fixedValuesEditor = FixedValuesParameterEditor(List(FixedExpressionValue("'expression'", "label")))
    val config            = ParameterConfig(None, Some(List(fixedValuesEditor)), None, None, None)

    EditorExtractor.extract(paramNotAnnotated, config) shouldBe ParameterEditors(fixedValuesEditor).some
  }

  test("determine editor by type enum") {
    val param = getSimpleParamByType[JavaSampleEnum]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
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
      ),
      SpelParameterEditor
    ).some
  }

  test("determine editor by type LocalDateTime") {
    val param = getSimpleParamByType[LocalDateTime]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
      DateTimeParameterEditor,
      SpelParameterEditor,
    ).some
  }

  test("determine editor by type LocalDate") {
    val param = getSimpleParamByType[LocalDate]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
      DateParameterEditor,
      SpelParameterEditor,
    ).some
  }

  test("determine editor by type LocalTime") {
    val param = getSimpleParamByType[LocalTime]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
      TimeParameterEditor,
      SpelParameterEditor,
    ).some
  }

  test("determine editor by type Duration") {
    val param = getSimpleParamByType[Duration]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
      DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
      SpelParameterEditor,
    ).some
  }

  test("determine editor by config for Duration") {
    val param  = getSimpleParamByType[Duration]
    val editor = DurationParameterEditor(timeRangeComponents = List(ChronoUnit.MINUTES))

    EditorExtractor.extract(param, ParameterConfig.empty.copy(editors = Some(List(editor)))) shouldBe ParameterEditors(
      editor
    ).some
  }

  test("determine editor by type Period") {
    val param = getSimpleParamByType[Period]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
      PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
      SpelParameterEditor,
    ).some
  }

  test("determine editor by type Cron") {
    val param = getSimpleParamByType[Cron]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe ParameterEditors(
      CronParameterEditor,
      SpelParameterEditor,
    ).some
  }

  test("determine editor by type Charsequence") {
    val charseqParam = getSimpleParamByType[CharSequence]
    val stringParam  = getSimpleParamByType[String]

    val expectedEditor = ParameterEditors(
      SpelParameterEditor,
      SpelTemplateParameterEditor,
    ).some
    EditorExtractor.extract(charseqParam, ParameterConfig.empty) shouldBe expectedEditor
    EditorExtractor.extract(stringParam, ParameterConfig.empty) shouldBe expectedEditor
  }

  private def getFirstParam(name: String, params: Class[_]*) = {
    val parameter = this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
    ParameterData(parameter, Typed.typedClass(parameter.getType))
  }

  private def getSimpleParamByType[T: ClassTag] = {
    val parameter = this.getClass.getDeclaredMethods
      .find(_.getName == "simpleParams")
      .flatMap(_.getParameters.find(_.getType == implicitly[ClassTag[T]].runtimeClass))
      .get
    ParameterData(parameter, Typed.typedClass(parameter.getType))
  }

}
