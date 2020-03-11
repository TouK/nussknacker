package pl.touk.nussknacker.ui.definition.additionalproperty

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, MandatoryValueValidator}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig

class AdditionalPropertyValidatorsDeterminerChainTest extends FunSuite with Matchers {

  test("determine validator based on config") {
    val config = AdditionalPropertyConfig(
      None,
      None,
      Some(List(MandatoryValueValidator)),
      None
    )

    val determined = AdditionalPropertyValidatorDeterminerChain(config).determine()

    determined shouldBe List(MandatoryValueValidator)
  }

  test("determine validator based on fixed value editor") {
    val possibleValues = List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))
    val config = AdditionalPropertyConfig(
      None,
      Some(FixedValuesParameterEditor(possibleValues)),
      None,
      None
    )

    val determined = AdditionalPropertyValidatorDeterminerChain(config).determine()

    determined shouldBe List(FixedValuesValidator(possibleValues))
  }
}
