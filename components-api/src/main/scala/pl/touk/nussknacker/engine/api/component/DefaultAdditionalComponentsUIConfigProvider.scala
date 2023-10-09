package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalComponentsUIConfigProvider(
    processingTypeToConfig: Map[String, Map[ComponentId, SingleComponentConfigWithoutId]]
) extends AdditionalComponentsUIConfigProvider {

  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentConfigWithoutId] =
    processingTypeToConfig.getOrElse(processingType, Map.empty)

}
