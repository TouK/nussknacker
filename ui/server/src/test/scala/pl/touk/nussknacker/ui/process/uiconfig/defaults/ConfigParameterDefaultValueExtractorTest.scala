package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.definition.defaults.NodeDefinition

import scala.reflect.ClassTag

class ConfigParameterDefaultValueExtractorTest extends FlatSpec with Matchers {
  private val config = new ParamDefaultValueConfig(Map("definedNode" -> Map("definedParam" -> ParameterConfig(Some("Idea"), None, None))))
  private val extractor = new ConfigParameterDefaultValueExtractor(config)
  private val node = NodeDefinition("definedNode", Nil)
  behavior of "ConfigParameterDefaultValueExtractor"

  private def verifyExtractor[T:ClassTag](paramName: String, evaluatesTo: Option[String]) = {
    val param = Parameter[T](paramName)
    it should s"evaluate $param to $evaluatesTo" in {
      extractor.evaluateParameterDefaultValue(node, param) shouldBe evaluatesTo
    }
  }

  verifyExtractor[Integer]("undefinedParameter", evaluatesTo = None)
  verifyExtractor[Integer]("definedParam", evaluatesTo = Some("Idea"))
}
