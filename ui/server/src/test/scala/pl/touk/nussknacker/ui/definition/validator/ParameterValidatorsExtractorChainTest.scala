package pl.touk.nussknacker.ui.definition.validator

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.definition.{MandatoryValueValidator, Parameter}
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.Typed


class ParameterValidatorsExtractorChainTest extends FlatSpec with Matchers {
  behavior of "ParameterValidatorsExtractorChain"

  it should "extract validators from parameter" in {
    val param = new Parameter("param", Typed(ClazzRef[String]), classOf[String], None, List(MandatoryValueValidator))
    val config = ParameterConfig.empty

    val validators = ParameterValidatorsExtractorChain(config).evaluate(param)

    validators shouldBe List(MandatoryValueValidator)
  }

  it should "extract validators from config" in {
    val param = new Parameter("param", Typed(ClazzRef[String]), classOf[String], None)
    val config = ParameterConfig(None, None, Some(List(MandatoryValueValidator)))

    val validators = ParameterValidatorsExtractorChain(config).evaluate(param)

    validators shouldBe List(MandatoryValueValidator)
  }

  it should "override validators based on annotation with those from config" in {
    val param = new Parameter("param", Typed(ClazzRef[String]), classOf[String], None, List(MandatoryValueValidator))
    val config = ParameterConfig(None, None, Some(List.empty))

    val validators = ParameterValidatorsExtractorChain(config).evaluate(param)

    validators shouldBe List.empty
  }
}
