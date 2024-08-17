package pl.touk.nussknacker.ui.definition.scenarioproperty

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig

class UiScenarioPropertyEditorDeterminerTest extends AnyFunSuite with Matchers {

  test("should determine configured editor") {
    val configured = FixedValuesParameterEditor(List(FixedExpressionValue("a", "a")))

    val determined =
      UiScenarioPropertyEditorDeterminer.determine(
        SingleScenarioPropertyConfig(None, Some(configured), None, None, None)
      )

    determined shouldBe configured
  }

  test("should determine StringParameter editor for scenario property by default") {
    val determined = UiScenarioPropertyEditorDeterminer.determine(SingleScenarioPropertyConfig.empty)

    determined shouldBe StringParameterEditor
  }

}
