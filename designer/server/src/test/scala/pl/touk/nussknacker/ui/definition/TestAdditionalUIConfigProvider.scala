package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
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

  val componentAdditionalConfigMap: Map[DesignerWideComponentId, ComponentAdditionalConfig] = Map(
    DesignerWideComponentId("streaming-service-enricher") -> ComponentAdditionalConfig(
      parameterConfigs = Map(
        "param" -> ParameterAdditionalUIConfig(
          required = true,
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

  override def getAllForProcessingType(
      processingType: String
  ): Map[DesignerWideComponentId, ComponentAdditionalConfig] =
    if (processingType == TestProcessingTypes.Streaming)
      componentAdditionalConfigMap
    else
      Map.empty

  override def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig] =
    if (processingType == TestProcessingTypes.Streaming)
      scenarioPropertyConfigOverride
    else
      Map.empty

}
