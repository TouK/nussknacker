package pl.touk.nussknacker.ui.definition.defaults

import org.scalatest.{FlatSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.defaults.NodeDefinition
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

class DefaultValueDeterminerChainTest extends FunSuite with Matchers {

  private val confMap = Map("node1" -> Map("param1" -> ParameterConfig(defaultValue = Some("123"), editor = None, None)))
  private val param1 = Parameter[Int]("param1")
  private val param2 = Parameter[Int]("param=2")
  private val node = NodeDefinition("node1", List(param1, param2))

  private val determiner = DefaultValueDeterminerChain(ParamDefaultValueConfig(confMap), ModelClassLoader.empty)

  test("determine default value by type") {
    determiner.determineParameterDefaultValue(node, param2) shouldBe Some("0")
  }

  test("determine default value by config") {
    determiner.determineParameterDefaultValue(node, param1) shouldBe Some("123")
  }

}
