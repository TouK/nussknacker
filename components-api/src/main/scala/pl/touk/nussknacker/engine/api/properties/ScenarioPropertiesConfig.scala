package pl.touk.nussknacker.engine.api.properties

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig

@JsonCodec case class ScenarioPropertiesConfig(
    parameterConfig: Map[String, ScenarioPropertiesParameterConfig],
    docsIconConfig: Option[ScenarioPropertiesDocsUrlConfig]
)

object ScenarioPropertiesConfig {
  def empty(): ScenarioPropertiesConfig = { ScenarioPropertiesConfig(Map.empty, None) }

  def fromParameterMap(parameterMap: Map[String, ScenarioPropertiesParameterConfig]): ScenarioPropertiesConfig = {
    ScenarioPropertiesConfig(parameterMap, None)
  }

}

@JsonCodec case class ScenarioPropertiesDocsUrlConfig(docsUrl: String)
