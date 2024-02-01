package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinition private (
    components: List[ComponentDefinitionWithImplementation],
    expressionConfig: ExpressionConfigDefinition,
    settings: ClassExtractionSettings
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  def withComponent(component: ComponentDefinitionWithImplementation): ModelDefinition = {
    withComponents(List(component))
  }

  def withComponents(componentsToAdd: List[ComponentDefinitionWithImplementation]): ModelDefinition = {
    val newComponents = components ++ componentsToAdd
    checkDuplicates(newComponents)
    copy(components = newComponents)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[ComponentDefinitionWithImplementation] =
    getComponent(ComponentId(componentType, componentName))

  def getComponent(id: ComponentId): Option[ComponentDefinitionWithImplementation] = {
    components.find(_.id == id)
  }

  def filterComponents(predicate: ComponentDefinitionWithImplementation => Boolean): ModelDefinition =
    copy(components.filter(predicate))

  def mapComponents(
      f: ComponentDefinitionWithImplementation => ComponentDefinitionWithImplementation
  ): ModelDefinition =
    copy(components.map(f))

  private def checkDuplicates(components: List[ComponentDefinitionWithImplementation]): Unit = {
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
      components: List[ComponentDefinitionWithImplementation],
      expressionConfig: ExpressionConfigDefinition,
      settings: ClassExtractionSettings
  ): ModelDefinition =
    new ModelDefinition(List.empty, expressionConfig, settings).withComponents(components)

}
