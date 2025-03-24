package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentId, ProcessingMode, ScenarioPropertyConfig}
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData.DynamicComponentsStaticDefinitions

final class DesignerModelData(
    val modelData: ModelData,
    val scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    val fragmentPropertiesConfig: Map[String, ScenarioPropertyConfig],
    // We hold definitions as a cache - computing them is a quite costly operation (it invokes external services)
    val staticDefinitionForDynamicComponents: DynamicComponentsStaticDefinitions,
    val processingMode: ProcessingMode
)

object DesignerModelData {

  final case class DynamicComponentsStaticDefinitions(
      finalDefinitions: Map[ComponentId, ComponentStaticDefinition],
      // component definitions not enriched with ui config
      private val basicDefinitions: Option[Map[ComponentId, ComponentStaticDefinition]]
  ) {

    def basicDefinitionsUnsafe: Map[ComponentId, ComponentStaticDefinition] =
      basicDefinitions.getOrElse(
        throw new IllegalStateException("Basic definitions were requested but they are not precomputed")
      )

  }

}
