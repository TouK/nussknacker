package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinition private (
    components: List[ComponentDefinitionWithLogic],
    expressionConfig: ExpressionConfigDefinition,
    settings: ClassExtractionSettings
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  def withComponent(component: ComponentDefinitionWithLogic): ModelDefinition = {
    withComponents(List(component))
  }

  def withComponents(componentsToAdd: List[ComponentDefinitionWithLogic]): ModelDefinition = {
    val newComponents = components ++ componentsToAdd
    checkDuplicates(newComponents)
    copy(components = newComponents)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[ComponentDefinitionWithLogic] =
    getComponent(ComponentId(componentType, componentName))

  def getComponent(id: ComponentId): Option[ComponentDefinitionWithLogic] = {
    components.find(_.id == id)
  }

  def filterComponents(predicate: ComponentDefinitionWithLogic => Boolean): ModelDefinition =
    copy(components.filter(predicate))

  def mapComponents(
      f: ComponentDefinitionWithLogic => ComponentDefinitionWithLogic
  ): ModelDefinition =
    copy(components.map(f))

  private def checkDuplicates(components: List[ComponentDefinitionWithLogic]): Unit = {
    val duplicates = components
      .map(component => component.id -> component)
      .toGroupedMap
      .filter(_._2.size > 1)
      .keys
      .toList
      .sorted
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
      components: List[ComponentDefinitionWithLogic],
      expressionConfig: ExpressionConfigDefinition,
      settings: ClassExtractionSettings
  ): ModelDefinition =
    new ModelDefinition(List.empty, expressionConfig, settings).withComponents(components)

}
