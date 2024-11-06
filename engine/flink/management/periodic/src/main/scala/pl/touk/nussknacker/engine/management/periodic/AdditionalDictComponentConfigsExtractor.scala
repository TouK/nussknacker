package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.parameter.ValueInputWithDictEditor

object AdditionalDictComponentConfigsExtractor {

  // This function filters additional configs provided by AdditionalUIConfigProvider
  // to include only component and parameter configs with Dictionary editors.
  // This is done to reduce data sent to Flink as only configs regarding this editor are required to be known during execution.
  def getAdditionalConfigsWithDictParametersEditors(
      additionalComponentConfigs: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): Map[DesignerWideComponentId, ComponentAdditionalConfig] = {
    additionalComponentConfigs
      .map { case (componentId, componentAdditionalConfig) =>
        val parametersWithDictEditors = componentAdditionalConfig.parameterConfigs.filter {
          case (_, additionalUiConfig) =>
            additionalUiConfig.valueEditor match {
              case Some(_: ValueInputWithDictEditor) => true
              case _                                 => false
            }
        }
        componentId -> componentAdditionalConfig.copy(parameterConfigs = parametersWithDictEditors)
      }
      .filter(_._2.parameterConfigs.nonEmpty)
  }

}
