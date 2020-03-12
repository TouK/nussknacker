package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult
import pl.touk.nussknacker.ui.definition.UIParameter

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
    Option.empty
  }

}
