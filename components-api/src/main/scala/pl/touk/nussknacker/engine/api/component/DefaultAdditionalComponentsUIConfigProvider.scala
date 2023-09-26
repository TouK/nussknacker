package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalComponentsUIConfigProvider extends AdditionalComponentsUIConfigProvider {

  override def getAllForCategory(category: String): Map[ComponentId, SingleComponentUIConfig] = Map.empty

  override def getAll: Map[ComponentId, SingleComponentUIConfig] = Map.empty
}
