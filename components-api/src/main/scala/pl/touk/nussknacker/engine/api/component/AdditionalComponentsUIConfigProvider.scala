package pl.touk.nussknacker.engine.api.component

trait AdditionalComponentsUIConfigProvider extends Serializable {

  def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentUIConfig]

  def getAll: Map[ComponentId, SingleComponentUIConfig]

}
