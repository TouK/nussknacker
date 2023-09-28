package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalUIConfigProvider extends AdditionalUIConfigProvider {

  override def getComponentUIConfigs(processingType: String): Map[ComponentId, SingleComponentUIConfig] = Map.empty

  override def getAllComponentUIConfigs: Map[ComponentId, SingleComponentUIConfig] = Map.empty

  override def getAdditionalPropertiesUIConfigs(category: String): Map[String, AdditionalPropertyConfig] = Map.empty
}
