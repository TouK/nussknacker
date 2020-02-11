package pl.touk.nussknacker.ui.definition.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.defaults.NodeDefinition
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

class DefaultValueDeterminerChainTest extends FlatSpec with Matchers {
  behavior of "DefaultValueDeterminerChainTest"
  private val confMap = Map("node1" -> Map("param1" -> ParameterConfig(defaultValue = Some("123"), editor = None, None)))
  private val param1 = Parameter[Int]("param1")
  private val param2 = Parameter[Int]("param=2")
  private val node = NodeDefinition("node1", List(param1, param2))
  private val extractor = DefaultValueDeterminerChain(ParamDefaultValueConfig(confMap), ModelClassLoader.empty)
  it should "determine default value by type" in {
    extractor.determineParameterDefaultValue(node, param2) shouldBe Some("0")
  }
  it should "determine default value by config" in {
    extractor.determineParameterDefaultValue(node, param1) shouldBe Some("123")
  }
}
