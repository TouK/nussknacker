package pl.touk.nussknacker.engine.requestresponse.api.openapi

import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, JsonValidator, MandatoryParameterValidator}

object RequestResponseOpenApiSettings {

  val OutputSchemaProperty = "outputSchema"
  val InputSchemaProperty  = "inputSchema"
  private val emptySchema  = "{}"

  // TODO: add json schema validator
  val scenarioPropertiesConfig: Map[String, ScenarioPropertiesParameterConfig] = Map(
    InputSchemaProperty -> ScenarioPropertiesParameterConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryParameterValidator, JsonValidator)),
      Some("Input schema"),
      None
    ),
    OutputSchemaProperty -> ScenarioPropertiesParameterConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryParameterValidator, JsonValidator)),
      Some("Output schema"),
      None
    ),
  )

}
