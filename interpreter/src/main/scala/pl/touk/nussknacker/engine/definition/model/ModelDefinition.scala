package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentInfo
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinition private (
    components: Map[ComponentInfo, ComponentDefinitionWithImplementation],
    expressionConfig: ExpressionConfigDefinition,
    settings: ClassExtractionSettings
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  def withComponent(componentName: String, component: ComponentDefinitionWithImplementation): ModelDefinition = {
    withComponents(List(componentName -> component))
  }

  def withComponents(componentsToAdd: List[(String, ComponentDefinitionWithImplementation)]): ModelDefinition = {
    val newComponents = components.toList ++ componentsToAdd.map { case (componentName, component) =>
      ComponentInfo(component.componentType, componentName) -> component
    }
    checkDuplicates(newComponents)
    copy(components = newComponents.toMap)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[ComponentDefinitionWithImplementation] =
    getComponent(ComponentInfo(componentType, componentName))

  def getComponent(info: ComponentInfo): Option[ComponentDefinitionWithImplementation] = {
    components.get(info)
  }

  def filterComponents(predicate: (ComponentInfo, ComponentDefinitionWithImplementation) => Boolean): ModelDefinition =
    copy(
      components.filter(predicate tupled)
    )

  def mapComponents(
      f: ComponentDefinitionWithImplementation => ComponentDefinitionWithImplementation
  ): ModelDefinition =
    copy(
      components.mapValuesNow(f),
    )

  private def checkDuplicates(components: List[(ComponentInfo, ComponentDefinitionWithImplementation)]): Unit = {
    val duplicates = components.toGroupedMap
      .filter(_._2.size > 1)
      .keys
      .toList
      .sortBy(info => (info.`type`, info.name))
    if (duplicates.nonEmpty) {
      throw new DuplicatedComponentsException(duplicates)
    }
  }

}

class DuplicatedComponentsException(duplicates: List[ComponentInfo])
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
