package pl.touk.nussknacker.engine.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.definition.{BoolParameterEditor, DualParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, RawParameterEditor, SimpleParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.ParameterConfig

class EditorExtractorTest extends FunSuite with Matchers {

  private def notAnnotated(param: String) {}

  private def dualEditorAnnotated(@DualEditor(
    simpleEditor = new SimpleEditor(
      `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
      possibleValues = Array(new LabeledExpression(expression = "test", label = "test2"))
    ),
    defaultMode = DualEditorMode.SIMPLE
  ) param: String) {}

  private def dualEditorAnnotatedLazy(@DualEditor(
    simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
    defaultMode = DualEditorMode.SIMPLE
  ) param: LazyParameter[String]) {}

  private def simpleEditorAnnotated(@SimpleEditor(`type` = SimpleEditorType.BOOL_EDITOR) param: String) {}

  private def simpleEditorAnnotatedLazy(@SimpleEditor(`type` = SimpleEditorType.BOOL_EDITOR) param: LazyParameter[String]) {}

  private def rawEditorAnnotated(@RawEditor param: String) {}

  private def rawEditorAnnotatedLazy(@RawEditor param: LazyParameter[String]) {}

  private val paramNotAnnotated = getFirstParam("notAnnotated", classOf[String])

  private val paramDualEditorAnnotated = getFirstParam("dualEditorAnnotated", classOf[String])
  private val paramDualEditorLazyAnnotated = getFirstParam("dualEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramSimpleEditorAnnotated = getFirstParam("simpleEditorAnnotated", classOf[String])
  private val paramSimpleEditorLazyAnnotated = getFirstParam("simpleEditorAnnotatedLazy", classOf[LazyParameter[String]])

  private val paramRawEditorAnnotated = getFirstParam("rawEditorAnnotated", classOf[String])
  private val paramRawEditorAnnotatedLazy = getFirstParam("rawEditorAnnotatedLazy", classOf[LazyParameter[String]])


  test("assign None when no annotation detected") {
    EditorExtractor.extract(paramNotAnnotated) shouldBe None
  }

  test("detect @DualEditor annotation") {

    EditorExtractor.extract(paramDualEditorAnnotated) shouldBe
      Some(DualParameterEditor(
        simpleEditor = FixedValuesParameterEditor(
          possibleValues = List(FixedExpressionValue("test", "test2"))
        ),
        defaultMode = DualEditorMode.SIMPLE
      ))

    EditorExtractor.extract(paramDualEditorLazyAnnotated) shouldBe
      Some(DualParameterEditor(
        simpleEditor = StringParameterEditor,
        defaultMode = DualEditorMode.SIMPLE
      ))
  }

  test("detect @SimpleEditor annotation") {

    EditorExtractor.extract(paramSimpleEditorAnnotated) shouldBe
      Some(BoolParameterEditor)

    EditorExtractor.extract(paramSimpleEditorLazyAnnotated) shouldBe
      Some(BoolParameterEditor)
  }

  test("detect @RawEditor annotation") {

    EditorExtractor.extract(paramRawEditorAnnotated) shouldBe Some(RawParameterEditor)

    EditorExtractor.extract(paramRawEditorAnnotatedLazy) shouldBe Some(RawParameterEditor)
  }

  private def getFirstParam(name: String, params: Class[_]*) = {
    this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
  }
}
