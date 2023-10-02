package pl.touk.nussknacker.ui.definition.additionalproperty

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.component.{AdditionalPropertyConfig, ParameterConfig}

class UiAdditionalPropertyEditorDeterminerTest extends AnyFunSuite with Matchers {

  test("should determine configured editor") {
    val configured = FixedValuesParameterEditor(List(FixedExpressionValue("a", "a")))

    val determined = UiAdditionalPropertyEditorDeterminer.determine(AdditionalPropertyConfig(None, Some(configured), None, None))

    determined shouldBe configured
  }

  test("should determine StringParameter editor for additional property by default") {
    val determined = UiAdditionalPropertyEditorDeterminer.determine(AdditionalPropertyConfig.empty)

    determined shouldBe StringParameterEditor
  }
}
