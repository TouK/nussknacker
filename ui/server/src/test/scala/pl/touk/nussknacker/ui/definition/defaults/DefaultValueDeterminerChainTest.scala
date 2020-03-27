package pl.touk.nussknacker.ui.definition.defaults

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.ui.definition.UIParameter
import pl.touk.nussknacker.ui.definition.editor.JavaSampleEnum

class DefaultValueDeterminerChainTest extends FunSuite with Matchers {

  private val param1Config: ParameterConfig = ParameterConfig(defaultValue = Some("123"), editor = None, None, None)
  private val confMap = Map("node1" -> Map("param1" -> param1Config))

  private val uiParamInt = UIParameter(Parameter[Int]("param=2"), ParameterConfig.empty)
  private val uiFixedValuesParam = UIParameter(Parameter[JavaSampleEnum](name = "fixedVakuesParam"), ParameterConfig.empty)
  private val uiParamWithConfig = UIParameter(Parameter[Int]("param1"), param1Config)
  private val uiOptionalParam = UIParameter(Parameter.optional[Int]("optionalParam"), ParameterConfig.empty)

  private val node = UINodeDefinition("node1", List(uiParamInt, uiFixedValuesParam, uiParamWithConfig, uiOptionalParam))

  private val determiner = DefaultValueDeterminerChain(ParamDefaultValueConfig(confMap))

  test("determine default value by type") {
    determiner.determineParameterDefaultValue(node, uiParamInt) shouldBe Some("0")
  }

  test("determine default value by editor possible values") {
    determiner.determineParameterDefaultValue(node, uiFixedValuesParam) shouldBe Some("T(pl.touk.nussknacker.ui.definition.editor.JavaSampleEnum).FIRST_VALUE")
  }

  test("determine default value by config") {
    determiner.determineParameterDefaultValue(node, uiParamWithConfig) shouldBe Some("123")
  }

  test("choose empty expression as default value for optional parameters") {
    determiner.determineParameterDefaultValue(node, uiOptionalParam) shouldBe Some("")
  }
}
