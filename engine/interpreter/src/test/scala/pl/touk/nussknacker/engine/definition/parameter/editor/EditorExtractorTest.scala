package pl.touk.nussknacker.engine.definition.parameter.editor

import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period}

import com.cronutils.model.Cron
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.types.JavaSampleEnum

class EditorExtractorTest extends FunSuite with Matchers {

  private def notAnnotated(param: String) {}

  private def dualEditorAnnotated(@DualEditor(
    simpleEditor = new SimpleEditor(
      `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
      possibleValues = Array(new LabeledExpression(expression = "'test'", label = "test2"))
    ),
    defaultMode = DualEditorMode.SIMPLE
  ) param: String) {}

  private def dualEditorAnnotatedLazy(@DualEditor(
    simpleEditor = new SimpleEditor(`type` = SimpleEditorType.DATE_EDITOR),
    defaultMode = DualEditorMode.SIMPLE
  ) param: LazyParameter[String]) {}

  private def simpleEditorAnnotated(@SimpleEditor(`type` = SimpleEditorType.BOOL_EDITOR) param: String) {}

  private def simpleEditorAnnotatedLazy(@SimpleEditor(`type` = SimpleEditorType.BOOL_EDITOR) param: LazyParameter[String]) {}

  private def rawEditorAnnotated(@RawEditor param: String) {}

  private def rawEditorAnnotatedLazy(@RawEditor param: LazyParameter[String]) {}

  private def simpleParams(javaEnum: JavaSampleEnum, localDateTime: LocalDateTime,
                           localDate: LocalDate, localTime: LocalTime, duration: Duration, period: Period, cron: Cron) {}

  private val paramNotAnnotated = getFirstParam("notAnnotated", classOf[String])

  private val paramDualEditorAnnotated = getFirstParam("dualEditorAnnotated", classOf[String])
  private val paramDualEditorLazyAnnotated = getFirstParam("dualEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramSimpleEditorAnnotated = getFirstParam("simpleEditorAnnotated", classOf[String])
  private val paramSimpleEditorLazyAnnotated = getFirstParam("simpleEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramRawEditorAnnotated = getFirstParam("rawEditorAnnotated", classOf[String])
  private val paramRawEditorAnnotatedLazy = getFirstParam("rawEditorAnnotatedLazy", classOf[LazyParameter[String]])

  test("assign RawEditor when no annotation detected") {
    EditorExtractor.extract(paramNotAnnotated, ParameterConfig.empty) shouldBe Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
  }

  test("detect @DualEditor annotation") {

    EditorExtractor.extract(paramDualEditorAnnotated, ParameterConfig.empty) shouldBe
      Some(DualParameterEditor(
        simpleEditor = FixedValuesParameterEditor(
          possibleValues = List(FixedExpressionValue("'test'", "test2"))
        ),
        defaultMode = DualEditorMode.SIMPLE
      ))

    EditorExtractor.extract(paramDualEditorLazyAnnotated, ParameterConfig.empty) shouldBe
      Some(DualParameterEditor(
        simpleEditor = DateParameterEditor,
        defaultMode = DualEditorMode.SIMPLE
      ))
  }

  test("detect @SimpleEditor annotation") {

    EditorExtractor.extract(paramSimpleEditorAnnotated, ParameterConfig.empty) shouldBe
      Some(BoolParameterEditor)

    EditorExtractor.extract(paramSimpleEditorLazyAnnotated, ParameterConfig.empty) shouldBe
      Some(BoolParameterEditor)
  }

  test("detect @RawEditor annotation") {
    EditorExtractor.extract(paramRawEditorAnnotated, ParameterConfig.empty) shouldBe Some(RawParameterEditor)
    EditorExtractor.extract(paramRawEditorAnnotatedLazy, ParameterConfig.empty) shouldBe Some(RawParameterEditor)
  }

  test("determine editor by config") {
    val fixedValuesEditor = FixedValuesParameterEditor(List(FixedExpressionValue("'expression'", "label")))
    val config = ParameterConfig(None, Some(fixedValuesEditor), None, None)

    EditorExtractor.extract(paramNotAnnotated, config) shouldBe Some(fixedValuesEditor)
  }

  test("determine editor by type enum") {
    val param = getSimpleParamByName("javaEnum")

    EditorExtractor.extract(param, ParameterConfig.empty)  shouldBe Some(DualParameterEditor(FixedValuesParameterEditor(List(
      FixedExpressionValue(s"T(${classOf[JavaSampleEnum].getName}).${JavaSampleEnum.FIRST_VALUE.name()}", "first_value"),
      FixedExpressionValue(s"T(${classOf[JavaSampleEnum].getName}).${JavaSampleEnum.SECOND_VALUE.name()}", "second_value")
    )), DualEditorMode.SIMPLE))
  }

  test("determine editor by type LocalDateTime") {
    val param = getSimpleParamByName("localDateTime")

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe Some(DualParameterEditor(
      simpleEditor = DateTimeParameterEditor,
      defaultMode = DualEditorMode.SIMPLE
    ))
  }

  test("determine editor by type LocalDate") {
    val param = getSimpleParamByName("localDate")

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe Some(DualParameterEditor(
      simpleEditor = DateParameterEditor,
      defaultMode = DualEditorMode.SIMPLE
    ))
  }

  test("determine editor by type LocalTime") {
    val param = getSimpleParamByName("localTime")

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe Some(DualParameterEditor(
      simpleEditor = TimeParameterEditor,
      defaultMode = DualEditorMode.SIMPLE
    ))
  }

  test("determine editor by type Duration") {
    val param = getSimpleParamByName("duration")

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe Some(DualParameterEditor(
      simpleEditor = DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
      defaultMode = DualEditorMode.SIMPLE
    ))
  }

  test("determine editor by config for Duration") {
    val param = getSimpleParamByName("duration")
    val editor = DurationParameterEditor(timeRangeComponents = List(ChronoUnit.MINUTES))

    EditorExtractor.extract(param, ParameterConfig.empty.copy(editor = Some(editor))) shouldBe Some(editor)
  }

  test("determine editor by type Period") {
    val param = getSimpleParamByName("period")

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe Some(DualParameterEditor(
      simpleEditor = PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
      defaultMode = DualEditorMode.SIMPLE
    ))
  }

  test("determine editor by type Cron") {
    val param = getSimpleParamByName("cron")

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe Some(DualParameterEditor(
      simpleEditor = CronParameterEditor,
      defaultMode = DualEditorMode.SIMPLE
    ))
  }

  private def getFirstParam(name: String, params: Class[_]*) = {
    val parameter = this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
    ParameterData(parameter, Typed.typedClass(parameter.getType))
  }

  private def getSimpleParamByName(param: String) = {
    val parameter = this.getClass.getDeclaredMethods
      .find(_.getName == "simpleParams").flatMap(_.getParameters.find(_.getName == param)).get
    ParameterData(parameter, Typed.typedClass(parameter.getType))
  }
}
