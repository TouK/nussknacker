package pl.touk.nussknacker.ui.definition.validator

import org.scalatest.{FlatSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.{MandatoryValueValidator, Parameter}
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.Typed


class ParameterBasedValidatorsDeterminerChainTest extends FunSuite with Matchers {

  test("determine validators based on parameter") {
    val param = new Parameter("param", Typed[String], classOf[String], None, List(MandatoryValueValidator))
    val config = ParameterConfig.empty

    val validators = ParameterValidatorsDeterminerChain(config).determineValidators(param)

    validators shouldBe List(MandatoryValueValidator)
  }

  test("determine validators based on config") {
    val param = new Parameter("param", Typed[String], classOf[String], None)
    val config = ParameterConfig(None, None, Some(List(MandatoryValueValidator)))

    val validators = ParameterValidatorsDeterminerChain(config).determineValidators(param)

    validators shouldBe List(MandatoryValueValidator)
  }

  test("override validators based on annotation with those from config") {
    val param = new Parameter("param", Typed[String], classOf[String], None, List(MandatoryValueValidator))
    val config = ParameterConfig(None, None, Some(List.empty))

    val validators = ParameterValidatorsDeterminerChain(config).determineValidators(param)

    validators shouldBe List.empty
  }

}
