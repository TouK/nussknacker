package pl.touk.nussknacker.engine.api.component

trait AdditionalComponentsUIConfigProvider extends Serializable {

  def getAllForCategory(category: String): Map[ComponentId, SingleComponentConfig]

}
