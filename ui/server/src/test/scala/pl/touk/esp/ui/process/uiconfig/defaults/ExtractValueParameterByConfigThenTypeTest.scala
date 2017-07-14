package pl.touk.esp.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.esp.ui.api.NodeDefinition

class ExtractValueParameterByConfigThenTypeTest extends FlatSpec with Matchers {
  behavior of "ExtractValueParameterByConfigThenTypeTest"
  private val confMap = Map("node1" -> Map("param1" -> "123"))
  private val param1 = DefinitionExtractor.Parameter("param1", ClazzRef("int"))
  private val param2 = DefinitionExtractor.Parameter("param=2", ClazzRef("int"))
  private val node = NodeDefinition("node1", List(param1, param2))
  private val extractor = new TypeAfterConfig(new ParamDefaultValueConfig(confMap))
  it should "evaluate value by type" in {
    extractor.evaluateParameterDefaultValue(node, param2) shouldBe Some("0")
  }
  it should "evaluate value by config" in {
    extractor.evaluateParameterDefaultValue(node, param1) shouldBe Some("123")
  }
}
