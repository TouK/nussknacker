package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.ui.additionalconfig.AdditionalUIConfigProvider
import pl.touk.nussknacker.ui.additionalconfig.AdditionalUIConfigProvider.{
  AdditionalUIConfig,
  ParameterAdditionalUIConfig
}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes

object TestAdditionalUIConfigProvider extends AdditionalUIConfigProvider {
  val componentGroupName: ComponentGroupName = ComponentGroupName("someComponentGroup")
  val scenarioPropertyName                   = "someScenarioProperty1"

  val scenarioPropertyConfigDefault: Map[String, ScenarioPropertyConfig] =
    Map(
      scenarioPropertyName -> ScenarioPropertyConfig.empty.copy(
        defaultValue = Some("someDefault")
      )
    )

  val scenarioPropertyConfigOverride: Map[String, ScenarioPropertyConfig] =
    Map(
      scenarioPropertyName -> ScenarioPropertyConfig.empty.copy(
        defaultValue = Some("defaultOverride")
      )
    )

  override def getAllForProcessingType(processingType: String): Map[ComponentId, AdditionalUIConfig] = {
    if (processingType == TestProcessingTypes.Streaming) {
      Map(
        ComponentId("streaming-service-enricher") -> AdditionalUIConfig(
          parameterConfigs = Map(
            "paramStringEditor" -> ParameterAdditionalUIConfig(
              initialValue = Some(
                FixedExpressionValue(
                  "'default-from-additional-ui-config-provider'",
                  "default-from-additional-ui-config-provider"
                )
              ),
              hintText = Some("hint-text-from-additional-ui-config-provider"),
              valueEditor = None,
              valueCompileTimeValidation = None
            )
          ),
          componentGroup = Some(componentGroupName)
        )
      )
    } else {
      Map.empty
    }
  }

  override def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig] =
    if (processingType == TestProcessingTypes.Streaming)
      scenarioPropertyConfigOverride
    else
      Map.empty

}
