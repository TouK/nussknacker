package pl.touk.nussknacker.engine.api.component

trait AdditionalUIConfigProvider extends Serializable {

  def getComponentUIConfigs(processingType: String): Map[ComponentId, SingleComponentUIConfig]

  def getAllComponentUIConfigs: Map[ComponentId, SingleComponentUIConfig]

  def getAdditionalPropertiesUIConfigs(processingType: String): Map[String, AdditionalPropertyConfig]

}
