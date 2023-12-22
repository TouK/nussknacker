package pl.touk.nussknacker.ui.additionalconfig

import pl.touk.nussknacker.engine.api.component.{ComponentId, ScenarioPropertyConfig}

class DefaultAdditionalUIConfigProvider(
    processingTypeToConfig: Map[String, Map[ComponentId, AdditionalUIConfig]],
    processingTypeToAdditionalPropertiesConfig: Map[String, Map[String, ScenarioPropertyConfig]],
) extends AdditionalUIConfigProvider {

  override def getAllForProcessingType(processingType: String): Map[ComponentId, AdditionalUIConfig] =
    processingTypeToConfig.getOrElse(processingType, Map.empty)

  override def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig] =
    processingTypeToAdditionalPropertiesConfig.getOrElse(processingType, Map.empty)
}
