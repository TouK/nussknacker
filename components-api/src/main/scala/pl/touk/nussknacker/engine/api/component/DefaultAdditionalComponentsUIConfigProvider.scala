package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalComponentsUIConfigProvider extends AdditionalComponentsUIConfigProvider {

  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentUIConfig] = Map.empty

  override def getAll: Map[ComponentId, SingleComponentUIConfig] = Map.empty
}
