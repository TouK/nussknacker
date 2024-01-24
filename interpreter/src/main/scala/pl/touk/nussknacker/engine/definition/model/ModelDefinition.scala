package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinition private (
    components: Map[ComponentId, ComponentDefinitionWithImplementation],
    expressionConfig: ExpressionConfigDefinition,
    settings: ClassExtractionSettings
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  def withComponent(componentName: String, component: ComponentDefinitionWithImplementation): ModelDefinition = {
    withComponents(List(componentName -> component))
  }

  def withComponents(componentsToAdd: List[(String, ComponentDefinitionWithImplementation)]): ModelDefinition = {
    val newComponents = components.toList ++ componentsToAdd.map { case (componentName, component) =>
      ComponentId(component.componentType, componentName) -> component
    }
    checkDuplicates(newComponents)
    copy(components = newComponents.toMap)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[ComponentDefinitionWithImplementation] =
    getComponent(ComponentId(componentType, componentName))

  def getComponent(id: ComponentId): Option[ComponentDefinitionWithImplementation] = {
    components.get(id)
  }

  def filterComponents(predicate: (ComponentId, ComponentDefinitionWithImplementation) => Boolean): ModelDefinition =
    copy(
      components.filter(predicate tupled)
    )

  def mapComponents(
      f: ComponentDefinitionWithImplementation => ComponentDefinitionWithImplementation
  ): ModelDefinition =
    copy(
      components.mapValuesNow(f),
    )

  private def checkDuplicates(components: List[(ComponentId, ComponentDefinitionWithImplementation)]): Unit = {
    val duplicates = components.toGroupedMap
      .filter(_._2.size > 1)
      .keys
      .toList
      .sortBy(id => (id.`type`, id.name))
    if (duplicates.nonEmpty) {
      throw new DuplicatedComponentsException(duplicates)
    }
  }

}

class DuplicatedComponentsException(duplicates: List[ComponentId])
    extends Exception(
      s"Found duplicated components: ${duplicates.mkString(", ")}. Please correct model configuration."
    )

object ModelDefinition {

  def apply(
      components: List[(String, ComponentDefinitionWithImplementation)],
      expressionConfig: ExpressionConfigDefinition,
      settings: ClassExtractionSettings
  ): ModelDefinition =
    new ModelDefinition(Map.empty, expressionConfig, settings).withComponents(components)

}
