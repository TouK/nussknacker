package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalUIConfigProvider(
    processingTypeToConfig: Map[String, Map[DesignerWideComponentId, ComponentAdditionalConfig]],
    processingTypeToAdditionalPropertiesConfig: Map[String, Map[String, SingleScenarioPropertyConfig]],
) extends AdditionalUIConfigProvider {

  override def getAllForProcessingType(
      processingType: String
  ): Map[DesignerWideComponentId, ComponentAdditionalConfig] =
    processingTypeToConfig.getOrElse(processingType, Map.empty)

  override def getScenarioPropertiesUIConfigs(processingType: String): Map[String, SingleScenarioPropertyConfig] =
    processingTypeToAdditionalPropertiesConfig.getOrElse(processingType, Map.empty)
}
