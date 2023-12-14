package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider.SingleComponentConfigWithoutId
import pl.touk.nussknacker.engine.api.component._
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

  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentConfigWithoutId] = {
    if (processingType == TestProcessingTypes.Streaming) {
      Map(
        ComponentId("streaming-service-enricher") -> SingleComponentConfigWithoutId.zero.copy(
          params = Map(
            "paramStringEditor" -> ParameterConfig.empty.copy(
              defaultValue = Some("'default-from-additional-ui-config-provider'"),
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
