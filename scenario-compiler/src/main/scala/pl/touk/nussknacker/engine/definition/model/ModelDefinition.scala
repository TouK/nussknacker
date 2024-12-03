package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithImplementation, Components}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinition private (
    components: Components,
    expressionConfig: ExpressionConfigDefinition,
    settings: ClassExtractionSettings
) {

  def withComponent(component: ComponentDefinitionWithImplementation): ModelDefinition = {
    withComponents(List(component))
  }

  def withComponents(componentsToAdd: Components): ModelDefinition = {
    val newComponents = components.withComponents(componentsToAdd)
    copy(components = newComponents)
  }

  def withComponents(componentsToAdd: List[ComponentDefinitionWithImplementation]): ModelDefinition = {
    val newComponents = components.withComponents(componentsToAdd)
    copy(components = newComponents)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[ComponentDefinitionWithImplementation] =
    getComponent(ComponentId(componentType, componentName))

  def getComponent(id: ComponentId): Option[ComponentDefinitionWithImplementation] = {
    components.components.find(_.id == id)
  }

  def filterComponents(predicate: ComponentDefinitionWithImplementation => Boolean): ModelDefinition =
    copy(components = components.filter(predicate))

}

class DuplicatedComponentsException(duplicates: List[ComponentId])
    extends Exception(
      s"Found duplicated components: ${duplicates.mkString(", ")}. Please correct model configuration."
    )

object ModelDefinition {

  def apply(
      components: Components,
      expressionConfig: ExpressionConfigDefinition,
      settings: ClassExtractionSettings,
  ): ModelDefinition = new ModelDefinition(components, expressionConfig, settings)

}
