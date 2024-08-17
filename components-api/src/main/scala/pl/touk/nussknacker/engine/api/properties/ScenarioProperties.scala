package pl.touk.nussknacker.engine.api.properties

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig

@JsonCodec case class ScenarioProperties(
    propertiesConfig: Map[String, SingleScenarioPropertyConfig],
    docsUrl: Option[String] = None
) {

  // it will overwrite docsUrl, with the approach that the latest config is the proper one.
  def ++(newConfig: ScenarioProperties): ScenarioProperties = {
    this.copy(propertiesConfig = this.propertiesConfig ++ newConfig.propertiesConfig, newConfig.docsUrl)
  }

}

object ScenarioProperties {
  def empty(): ScenarioProperties = { ScenarioProperties(Map.empty, None) }

  def fromParameterMap(parameterMap: Map[String, SingleScenarioPropertyConfig]): ScenarioProperties = {
    ScenarioProperties(parameterMap, None)
  }

}
