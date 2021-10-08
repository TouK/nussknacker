package pl.touk.nussknacker.engine.definition.parameter.defaults

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[String] =
    if (parameters.isOptional) Some("") else None

}
