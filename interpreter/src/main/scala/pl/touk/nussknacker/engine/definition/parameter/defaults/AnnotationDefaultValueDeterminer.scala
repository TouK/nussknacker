package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.api.DefaultValue

object AnnotationDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[String] = {
    parameters.parameterData.getAnnotation[DefaultValue].map(_.value())
  }

}