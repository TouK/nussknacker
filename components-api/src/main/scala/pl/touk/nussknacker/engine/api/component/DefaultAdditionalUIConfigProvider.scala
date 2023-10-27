package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider.SingleComponentConfigWithoutId

class DefaultAdditionalUIConfigProvider(
    processingTypeToConfig: Map[String, Map[ComponentId, SingleComponentConfigWithoutId]],
    processingTypeToAdditionalPropertiesConfig: Map[String, Map[String, ScenarioPropertyConfig]],
) extends AdditionalUIConfigProvider {

  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentConfigWithoutId] =
    processingTypeToConfig.getOrElse(processingType, Map.empty)

  override def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig] =
    processingTypeToAdditionalPropertiesConfig.getOrElse(processingType, Map.empty)
}
