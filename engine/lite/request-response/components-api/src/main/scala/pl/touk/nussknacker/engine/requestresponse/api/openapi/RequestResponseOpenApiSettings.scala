package pl.touk.nussknacker.engine.requestresponse.api.openapi

import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{
  JsonExpressionValidator,
  JsonParameterEditor,
  MandatoryExpressionValidator
}

object RequestResponseOpenApiSettings {

  val OutputSchemaProperty = "outputSchema"
  val InputSchemaProperty  = "inputSchema"
  private val emptySchema  = "{}"

  // TODO: add json schema validator
  val scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig] = Map(
    InputSchemaProperty -> ScenarioPropertyConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryExpressionValidator, JsonExpressionValidator)),
      Some("Input schema"),
      None
    ),
    OutputSchemaProperty -> ScenarioPropertyConfig(
      Some(emptySchema),
      Some(JsonParameterEditor),
      Some(List(MandatoryExpressionValidator, JsonExpressionValidator)),
      Some("Output schema"),
      None
    ),
  )

}
