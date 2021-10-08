package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.definition.parameter.ParameterData

trait ParameterDefaultValueDeterminer {
  def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[String]
}


case class DefaultValueDeterminerParameters(parameterData: ParameterData,
                                            isOptional: Boolean,
                                            parameterConfig: ParameterConfig,
                                            determinedEditor: Option[ParameterEditor])
