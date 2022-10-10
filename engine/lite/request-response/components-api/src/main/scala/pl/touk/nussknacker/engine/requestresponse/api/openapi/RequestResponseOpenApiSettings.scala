package pl.touk.nussknacker.engine.requestresponse.api.openapi

import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, JsonValidator, MandatoryParameterValidator}

object RequestResponseOpenApiSettings {

  val OutputSchemaProperty = "outputSchema"
  val InputSchemaProperty = "inputSchema"
  private val emptySchema = "{}"

  //TODO: add json schema validator
  val additionalPropertiesConfig: Map[String, AdditionalPropertyConfig] = Map(
    InputSchemaProperty -> AdditionalPropertyConfig(Some(emptySchema), Some(JsonParameterEditor), Some(List(MandatoryParameterValidator, JsonValidator)), Some("Input schema")),
    OutputSchemaProperty -> AdditionalPropertyConfig(Some(emptySchema), Some(JsonParameterEditor), Some(List(MandatoryParameterValidator, JsonValidator)), Some("Output schema")),
  )

}
