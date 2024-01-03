package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.ui.component.ComponentIdProvider

final case class ModelDefinitionWithComponentIds(
    components: List[(ComponentIdWithInfo, ComponentStaticDefinition)],
    expressionConfig: ExpressionConfigDefinition[ComponentStaticDefinition],
    settings: ClassExtractionSettings
)

object ModelDefinitionWithComponentIds {

  def apply(
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): ModelDefinitionWithComponentIds = {
    val transformedComponents =
      modelDefinition.components.toList.map { case (info, component) =>
        val id = componentIdProvider.createComponentId(processingType, info)
        ComponentIdWithInfo(id, info) -> component
      }
    ModelDefinitionWithComponentIds(
      transformedComponents,
      modelDefinition.expressionConfig,
      modelDefinition.settings
    )
  }

}

final case class ComponentIdWithInfo(id: ComponentId, info: ComponentInfo) {
  def name: String = info.name
}
