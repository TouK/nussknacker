package pl.touk.nussknacker.engine.util

import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.parameter.ValueInputWithDictEditor

object AdditionalComponentConfigsForRuntimeExtractor {

  // This is done to reduce data sent to Flink
  def getRequiredAdditionalConfigsForRuntime(
      additionalComponentConfigs: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): Map[DesignerWideComponentId, ComponentAdditionalConfig] = {
    getAdditionalConfigsWithDictParametersEditors(additionalComponentConfigs)
  }

  // This function filters additional configs provided by AdditionalUIConfigProvider
  // to include only component and parameter configs with Dictionary editors.
  private def getAdditionalConfigsWithDictParametersEditors(
      additionalComponentConfigs: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): Map[DesignerWideComponentId, ComponentAdditionalConfig] = additionalComponentConfigs
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
