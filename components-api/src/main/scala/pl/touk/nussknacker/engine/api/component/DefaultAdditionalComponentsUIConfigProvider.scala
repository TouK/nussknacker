package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalComponentsUIConfigProvider(processingTypeToConfig: Map[String, Map[ComponentId, SingleComponentUIConfig]]) extends AdditionalComponentsUIConfigProvider {

  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentUIConfig] = processingTypeToConfig.getOrElse(processingType, Map.empty)

}
