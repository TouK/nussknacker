package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition

final case class DesignerModelData(
    modelData: ModelData,
    // We hold this map as a cache - computing it is a quite costly operation (it invokes external services)
    staticDefinitionForDynamicComponents: Map[ComponentId, ComponentStaticDefinition],
    processingMode: ProcessingMode
) {

  def close(): Unit = {
    modelData.close()
  }

}
