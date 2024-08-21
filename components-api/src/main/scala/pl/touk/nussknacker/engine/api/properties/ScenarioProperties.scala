package pl.touk.nussknacker.engine.api.properties

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig

@JsonCodec case class ScenarioProperties(
    propertiesConfig: Map[String, ScenarioPropertyConfig],
    docsUrl: Option[String] = None
)

object ScenarioProperties {
  def empty(): ScenarioProperties = { ScenarioProperties(Map.empty, None) }

  def fromParameterMap(parameterMap: Map[String, ScenarioPropertyConfig]): ScenarioProperties = {
    ScenarioProperties(parameterMap, None)
  }

}
