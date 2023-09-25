package pl.touk.nussknacker.engine.api.component

class DefaultAdditionalComponentsUIConfigProvider extends AdditionalComponentsUIConfigProvider {

  override def getAllForCategory(category: String): Map[String, SingleComponentConfig] = Map.empty

}
