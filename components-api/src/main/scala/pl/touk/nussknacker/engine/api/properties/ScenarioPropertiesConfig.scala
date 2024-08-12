package pl.touk.nussknacker.engine.api.properties

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig

@JsonCodec case class ScenarioPropertiesConfig (parameterConfig: Map[String, ScenarioPropertiesParameterConfig], docsIconConfig: Option[ScenarioPropertiesDocsUrlConfig])

@JsonCodec case class ScenarioPropertiesDocsUrlConfig(docsUrl: String, docsIconPath: String)
