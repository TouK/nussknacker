package pl.touk.nussknacker.ui.definition.defaults

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIParameter

class EditorPossibleValuesBasedDefaultValueDeterminerTest extends FunSuite with Matchers {

  private val definition = UINodeDefinition("id", List())

  test("determine default param value from first value from fixed values editor possible values") {
    val fixedValuesEditor = Some(FixedValuesParameterEditor(List(
      FixedExpressionValue("expr1", "label1"),
      FixedExpressionValue("expr2", "label2")
    )))

    determine(fixedValuesEditor) shouldBe Some("expr1")
  }

  test("determine default param value from first value from fixed values editor possible values in dual mode") {
    val fixedValuesEditor = Some(DualParameterEditor(FixedValuesParameterEditor(List(
      FixedExpressionValue("expr1", "label1"),
      FixedExpressionValue("expr2", "label2")
    )), DualEditorMode.SIMPLE))

    determine(fixedValuesEditor) shouldBe Some("expr1")
  }

  test("not determine default param value from editors without possible values") {
    val stringParam = Some(StringParameterEditor)
    val booleanParam = Some(BoolParameterEditor)
    val rawParameterEditor = Some(RawParameterEditor)

    determine(stringParam) shouldBe None

    determine(booleanParam) shouldBe None

    determine(rawParameterEditor) shouldBe None
  }

  private def determine(editor: Option[ParameterEditor]) = {
    EditorPossibleValuesBasedDefaultValueDeterminer.determineParameterDefaultValue(
      definition,
      createUIParameter(
        Parameter.optional[String]("id").copy(editor = editor)
      )
    )
  }
}
