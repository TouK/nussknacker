package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.definition.defaults.NodeDefinition
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

class DefaultValueExtractorChainTest extends FlatSpec with Matchers {
  behavior of "DefaultValueExtractorChainTest"
  private val confMap = Map("node1" -> Map("param1" -> ParameterConfig(defaultValue = Some("123"), editor = None)))
  private val param1 = Parameter[Integer]("param1")
  private val param2 = Parameter[Integer]("param=2")
  private val node = NodeDefinition("node1", List(param1, param2))
  private val extractor = DefaultValueExtractorChain(ParamDefaultValueConfig(confMap), ModelClassLoader.empty)
  it should "evaluate value by type" in {
    extractor.evaluateParameterDefaultValue(node, param2) shouldBe Some("0")
  }
  it should "evaluate value by config" in {
    extractor.evaluateParameterDefaultValue(node, param1) shouldBe Some("123")
  }
}
