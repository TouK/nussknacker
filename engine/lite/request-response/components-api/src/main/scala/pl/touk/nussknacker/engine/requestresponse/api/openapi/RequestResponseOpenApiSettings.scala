package pl.touk.nussknacker.engine.requestresponse.api.openapi

import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, JsonValidator, MandatoryParameterValidator}

object RequestResponseOpenApiSettings {

  val OutputSchemaProperty = "outputSchema"
  val InputSchemaProperty  = "inputSchema"
  private val emptySchema  = "{}"

  // TODO: add json schema validator
  val scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig] = Map(
    InputSchemaProperty -> ScenarioPropertyConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryParameterValidator, JsonValidator)),
      Some("Input schema"),
      None
    ),
    OutputSchemaProperty -> ScenarioPropertyConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryParameterValidator, JsonValidator)),
      Some("Output schema"),
      None
    ),
  )

}
