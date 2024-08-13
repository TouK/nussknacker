package pl.touk.nussknacker.engine.api.properties

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig

@JsonCodec case class ScenarioPropertiesConfig(
    parameterConfig: Map[String, ScenarioPropertiesParameterConfig],
    docsIconConfig: Option[ScenarioPropertiesDocsUrlConfig]
) {

  // it will overwrite docsIcon, with the approach that the latest config is the proper one.
  def ++(newConfig: ScenarioPropertiesConfig): ScenarioPropertiesConfig = {
    this.copy(parameterConfig = this.parameterConfig ++ newConfig.parameterConfig, newConfig.docsIconConfig)
  }

}

object ScenarioPropertiesConfig {
  def empty(): ScenarioPropertiesConfig = { ScenarioPropertiesConfig(Map.empty, None) }

  def fromParameterMap(parameterMap: Map[String, ScenarioPropertiesParameterConfig]): ScenarioPropertiesConfig = {
    ScenarioPropertiesConfig(parameterMap, None)
  }

}

@JsonCodec case class ScenarioPropertiesDocsUrlConfig(docsUrl: String)
