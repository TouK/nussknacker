package pl.touk.nussknacker.ui.definition.scenarioproperty

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  FixedValuesValidator,
  MandatoryParameterValidator
}

class ScenarioPropertyValidatorsDeterminerChainTest extends AnyFunSuite with Matchers {

  test("determine property validator based on config") {
    val config = ScenarioPropertyConfig(
      None,
      None,
      Some(List(MandatoryParameterValidator)),
      None,
      None
    )

    val determined = ScenarioPropertyValidatorDeterminerChain(config).determine()

    determined shouldBe List(MandatoryParameterValidator)
  }

  test("determine property validator based on fixed value editor") {
    val possibleValues = List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))
    val config = ScenarioPropertyConfig(
      None,
      Some(FixedValuesParameterEditor(possibleValues)),
      None,
      None,
      None
    )

    val determined = ScenarioPropertyValidatorDeterminerChain(config).determine()

    determined shouldBe List(FixedValuesValidator(possibleValues))
  }

}
