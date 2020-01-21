package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.defaults.NodeDefinition

class EditorPossibleValuesBasedDefaultValueExtractorTest extends FunSuite with Matchers {

  private val definition = NodeDefinition("id", List())

  test("extract first value from fixed values editor possible values as default param value") {
    val fixedValuesEditor = Some(FixedValuesParameterEditor(List(
      FixedExpressionValue("expr1", "label1"),
      FixedExpressionValue("expr2", "label2")
    )))

    evaluate(fixedValuesEditor) shouldBe Some("expr1")
  }

  test("not extract default param value from editors without possible values") {
    val stringParam = Some(StringParameterEditor)
    val booleanParam = Some(BoolParameterEditor)
    val rawParameterEditor = Some(RawParameterEditor)

    evaluate(stringParam) shouldBe None

    evaluate(booleanParam) shouldBe None

    evaluate(rawParameterEditor) shouldBe None
  }

  private def evaluate(editor: Option[ParameterEditor]) = {
    EditorPossibleValuesBasedDefaultValueExtractor.evaluateParameterDefaultValue(
      definition,
      Parameter("id", Typed[String], classOf[String], editor)
    )
  }
}
