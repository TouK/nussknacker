package pl.touk.nussknacker.engine.requestresponse.api.openapi

import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, JsonValidator, MandatoryParameterValidator}

object RequestResponseOpenApiSettings {

  val OutputSchemaProperty = "outputSchema"
  val InputSchemaProperty  = "inputSchema"
  private val emptySchema  = "{}"

  // TODO: add json schema validator
  val scenarioPropertiesConfig: Map[String, SingleScenarioPropertyConfig] = Map(
    InputSchemaProperty -> SingleScenarioPropertyConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryParameterValidator, JsonValidator)),
      Some("Input schema"),
      None
    ),
    OutputSchemaProperty -> SingleScenarioPropertyConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryParameterValidator, JsonValidator)),
      Some("Output schema"),
      None
    ),
  )

}
