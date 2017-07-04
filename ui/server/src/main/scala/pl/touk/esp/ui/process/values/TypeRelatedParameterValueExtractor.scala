package pl.touk.esp.ui.process.values

import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.ui.api.NodeDefinition

object TypeRelatedParameterValueExtractor extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: DefinitionExtractor.Parameter): Option[String] = {
    Some(evaluateTypeRelatedParamValue(parameter.name, parameter.typ.refClazzName))
  }

  private[values] def evaluateTypeRelatedParamValue(name: String, refClassName: String): String = {
    refClassName match {
      case "long" | "short" | "int" | "java.lang.Number" => "0"
      case "float" | "double" | "java.math.BigDecimal" => "0.0"
      case "boolean" => "true"
      case "java.lang.String" => "''"
      case "java.util.List" => "{}"
      case "java.util.Map" => "{:}"
      case _ => s"#$name"
    }
  }

}
