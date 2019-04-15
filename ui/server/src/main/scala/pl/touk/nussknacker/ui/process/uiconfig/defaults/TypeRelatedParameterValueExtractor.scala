package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}

object TypeRelatedParameterValueExtractor extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: Parameter): Option[String] = {
    Some(evaluateTypeRelatedParamValue(parameter.name, parameter.typ.objType.klass.getName))
  }

  private[defaults] def evaluateTypeRelatedParamValue(name: String, refClassName: String): String = {
    refClassName match {
      case "long" | "short" | "int" | "java.lang.Number" => "0"
      case "float" | "double" | "java.math.BigDecimal" => "0.0"
      case "boolean" | "java.lang.Boolean" => "true"
      case "java.lang.String" => "''"
      case "java.util.List" => "{}"
      case "java.util.Map" => "{:}"
      case _ => s"#$name"
    }
  }

}
