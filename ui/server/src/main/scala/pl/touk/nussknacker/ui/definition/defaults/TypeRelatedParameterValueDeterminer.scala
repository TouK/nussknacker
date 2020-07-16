package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult
import pl.touk.nussknacker.restmodel.definition.UIParameter

protected object TypeRelatedParameterValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(nodeDefinition: UINodeDefinition,
                                              parameter: UIParameter): Option[String] = {
    val klass = parameter.typ match {
      case s: SingleTypingResult =>
        Some(s.objType.klass)
      case _ =>
        None
    }
    klass.flatMap(determineTypeRelatedDefaultParamValue)
  }

  private[defaults] def determineTypeRelatedDefaultParamValue(className: Class[_]): Option[String] = {
    // TODO: use classes instead of class names
    Option(className).map(_.getName).collect {
      case "long" | "short" | "int" | "java.lang.Number" | "java.lang.Long" | "java.lang.Short" | "java.lang.Integer" | "java.math.BigInteger" => "0"
      case "float" | "double" | "java.math.BigDecimal" | "java.lang.Float" | "java.lang.Double" => "0.0"
      case "boolean" | "java.lang.Boolean" => "true"
      case "java.lang.String" => "''"
      case "java.util.List" => "{}"
      case "java.util.Map" => "{:}"
    }
  }

}
