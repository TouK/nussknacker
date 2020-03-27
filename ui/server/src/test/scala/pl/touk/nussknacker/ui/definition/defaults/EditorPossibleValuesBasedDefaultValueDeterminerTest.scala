package pl.touk.nussknacker.ui.definition.defaults

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.ui.definition.UIParameter

class EditorPossibleValuesBasedDefaultValueDeterminerTest extends FunSuite with Matchers {

  private val definition = UINodeDefinition("id", List())

  test("determine default param value from first value from fixed values editor possible values") {
    val fixedValuesEditor = Some(FixedValuesParameterEditor(List(
      FixedExpressionValue("expr1", "label1"),
      FixedExpressionValue("expr2", "label2")
    )))

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
      UIParameter(
        Parameter("id", Typed[String], classOf[String], editor, validators = List.empty, additionalVariables = Map.empty, branchParam = false),
        ParameterConfig.empty
      )
    )
  }
}
