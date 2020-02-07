package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}

object TypeRelatedParameterValueExtractor extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: Parameter): Option[String] = {
    val klass = parameter.typ match {
      case s: SingleTypingResult =>
        s.objType.klass
      case _ =>
        // TOOD: what should happen here?
        classOf[Any]
    }
    Some(evaluateTypeRelatedParamValue(parameter.name, klass.getName))
  }

  private[defaults] def evaluateTypeRelatedParamValue(name: String, className: String): String = {
    className match {
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
