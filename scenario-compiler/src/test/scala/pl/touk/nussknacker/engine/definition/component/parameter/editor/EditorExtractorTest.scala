package pl.touk.nussknacker.engine.definition.component.parameter.editor

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
      @SimpleEditor(
        `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
        possibleValues = Array(new LabeledExpression(expression = "'test'", label = "test2")),
      )
      @SpelEditor
      param: String
  ) = ()

  private def dualEditorAnnotatedLazy(
      @SimpleEditor(`type` = SimpleEditorType.DATE_EDITOR)
      @SpelEditor
      param: LazyParameter[String]
  ) = ()

  private def simpleEditorAnnotated(@SimpleEditor(`type` = SimpleEditorType.BOOL_EDITOR) param: String) = ()

  private def simpleEditorAnnotatedLazy(
      @SimpleEditor(`type` = SimpleEditorType.BOOL_EDITOR) param: LazyParameter[String]
  ) = ()

  private def rawEditorAnnotated(@SpelEditor param: String) = ()

  private def rawEditorAnnotatedLazy(@SpelEditor param: LazyParameter[String]) = ()

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
    EditorExtractor.extract(paramNotAnnotated, ParameterConfig.empty) shouldBe List(
      SpelParameterEditor,
      SpelTemplateParameterEditor,
    )
  }

  test("detect either @SimpleEditor and @SpelEditor annotations") {

    EditorExtractor.extract(paramDualEditorAnnotated, ParameterConfig.empty) shouldBe
      List(
        FixedValuesParameterEditor(
          possibleValues = List(FixedExpressionValue("'test'", "test2"))
        ),
        SpelParameterEditor,
      )

    EditorExtractor.extract(paramDualEditorLazyAnnotated, ParameterConfig.empty) shouldBe
      List(
        DateParameterEditor,
        SpelParameterEditor,
      )
  }

  test("detect @SimpleEditor annotation") {

    EditorExtractor.extract(paramSimpleEditorAnnotated, ParameterConfig.empty) shouldBe
      List(BoolParameterEditor)

    EditorExtractor.extract(paramSimpleEditorLazyAnnotated, ParameterConfig.empty) shouldBe
      List(BoolParameterEditor)
  }

  test("detect @SpelEditor annotation") {
    EditorExtractor.extract(paramRawEditorAnnotated, ParameterConfig.empty) shouldBe List(SpelParameterEditor)
    EditorExtractor.extract(paramRawEditorAnnotatedLazy, ParameterConfig.empty) shouldBe List(SpelParameterEditor)
  }

  test("determine editor by config") {
    val fixedValuesEditor = FixedValuesParameterEditor(List(FixedExpressionValue("'expression'", "label")))
    val config            = ParameterConfig(None, Some(List(fixedValuesEditor)), None, None, None)

    EditorExtractor.extract(paramNotAnnotated, config) shouldBe List(fixedValuesEditor)
  }

  test("determine editor by type enum") {
    val param = getSimpleParamByType[JavaSampleEnum]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
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
    )
  }

  test("determine editor by type LocalDateTime") {
    val param = getSimpleParamByType[LocalDateTime]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
      DateTimeParameterEditor,
      SpelParameterEditor,
    )
  }

  test("determine editor by type LocalDate") {
    val param = getSimpleParamByType[LocalDate]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
      DateParameterEditor,
      SpelParameterEditor,
    )
  }

  test("determine editor by type LocalTime") {
    val param = getSimpleParamByType[LocalTime]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
      TimeParameterEditor,
      SpelParameterEditor,
    )
  }

  test("determine editor by type Duration") {
    val param = getSimpleParamByType[Duration]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
      DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
      SpelParameterEditor,
    )
  }

  test("determine editor by config for Duration") {
    val param  = getSimpleParamByType[Duration]
    val editor = DurationParameterEditor(timeRangeComponents = List(ChronoUnit.MINUTES))

    EditorExtractor.extract(param, ParameterConfig.empty.copy(editors = Some(List(editor)))) shouldBe List(editor)
  }

  test("determine editor by type Period") {
    val param = getSimpleParamByType[Period]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
      PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
      SpelParameterEditor,
    )
  }

  test("determine editor by type Cron") {
    val param = getSimpleParamByType[Cron]

    EditorExtractor.extract(param, ParameterConfig.empty) shouldBe List(
      CronParameterEditor,
      SpelParameterEditor,
    )
  }

  test("determine editor by type Charsequence") {
    val charseqParam = getSimpleParamByType[CharSequence]
    val stringParam  = getSimpleParamByType[String]

    val expectedEditor = List(
      SpelParameterEditor,
      SpelTemplateParameterEditor,
    )
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
