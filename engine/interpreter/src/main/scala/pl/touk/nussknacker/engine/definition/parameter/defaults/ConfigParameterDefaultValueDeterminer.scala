package pl.touk.nussknacker.engine.definition.parameter.defaults

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[String] = {
    parameters.parameterConfig.defaultValue
  }

}