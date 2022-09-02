package pl.touk.nussknacker.engine.requestresponse.api.openapi

import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, MandatoryParameterValidator}

object RequestResponseOpenApiSettings {

  val OutputSchemaProperty = "outputSchema"
  val InputSchemaProperty = "inputSchema"
  val OPEN_API_VERSION = "3.1.0"

  private val emptySchema = "{}"

  val additionalPropertiesConfig: Map[String, AdditionalPropertyConfig] = Map(
    InputSchemaProperty -> AdditionalPropertyConfig(Some(emptySchema), Some(JsonParameterEditor), Some(List(MandatoryParameterValidator)), Some("Input schema")),
    OutputSchemaProperty -> AdditionalPropertyConfig(Some(emptySchema), Some(JsonParameterEditor), Some(List(MandatoryParameterValidator)), Some("Output schema")),
  )
}
