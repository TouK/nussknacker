package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult

protected object TypeRelatedParameterValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[String] = {
    val klass = parameters.parameterData.typing match {
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
