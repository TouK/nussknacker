package pl.touk.nussknacker.ui.definition.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIParameter

import scala.reflect.runtime.universe._

class ConfigParameterDefaultValueDeterminerTest extends FlatSpec with Matchers {
  private val config = new ParamDefaultValueConfig(Map("definedNode" -> Map("definedParam" -> ParameterConfig(Some("Idea"), None, None, None))))
  private val determiner = new ConfigParameterDefaultValueDeterminer(config)
  private val node = UINodeDefinition("definedNode", Nil)
  behavior of "ConfigParameterDefaultValueDeterminer"

  private def verifyDeterminer[T: TypeTag](paramName: String, determinedDefaultValue: Option[String]) = {
    val param = Parameter[T](paramName)
    it should s"determine default value of $param to $determinedDefaultValue" in {
      determiner.determineParameterDefaultValue(node, createUIParameter(param)) shouldBe determinedDefaultValue
    }
  }

  verifyDeterminer[Integer]("undefinedParameter", determinedDefaultValue = None)
  verifyDeterminer[Integer]("definedParam", determinedDefaultValue = Some("Idea"))
}
