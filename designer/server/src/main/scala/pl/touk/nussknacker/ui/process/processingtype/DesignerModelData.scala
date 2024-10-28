package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData.DynamicComponentsStaticDefinitions

final case class DesignerModelData(
    modelData: ModelData,
    // We hold definitions as a cache - computing them is a quite costly operation (it invokes external services)
    staticDefinitionForDynamicComponents: DynamicComponentsStaticDefinitions,
    processingMode: ProcessingMode
) {

  def close(): Unit = {
    modelData.close()
  }

}

object DesignerModelData {

  final case class DynamicComponentsStaticDefinitions(
      finalDefinitions: Map[ComponentId, ComponentStaticDefinition],
      // components definitions without enrichments from an additional provider
      basicDefinitions: Option[Map[ComponentId, ComponentStaticDefinition]]
  ) {

    def basicDefinitionsUnsafe: Map[ComponentId, ComponentStaticDefinition] =
      basicDefinitions.getOrElse(
        throw new IllegalStateException("Basic definitions were requested but they are not precomputed")
      )

  }

}
