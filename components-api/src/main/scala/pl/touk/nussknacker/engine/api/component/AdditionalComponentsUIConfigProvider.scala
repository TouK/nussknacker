package pl.touk.nussknacker.engine.api.component

trait AdditionalComponentsUIConfigProvider {

  def getAllForCategory(category: String): Map[String, SingleComponentConfig]

}
