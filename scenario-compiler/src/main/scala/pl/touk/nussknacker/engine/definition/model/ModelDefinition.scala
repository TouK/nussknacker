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

  import pl.touk.nussknacker.engine.util.Implicits._

  def withComponent(component: ComponentDefinitionWithImplementation): ModelDefinition = {
    withComponents(List(component))
  }

  def withComponents(componentsToAdd: Components): ModelDefinition = {
    val newComponents = components.copy(
      components = components.components ++ componentsToAdd.components,
      basicComponents = components.basicComponents ++ componentsToAdd.basicComponents
    )
    checkDuplicates(newComponents)
    copy(components = newComponents)
  }

  def withComponents(componentsToAdd: List[ComponentDefinitionWithImplementation]): ModelDefinition = {
    val newComponents = components.copy(
      components = components.components ++ componentsToAdd,
      basicComponents = components.basicComponents ++ componentsToAdd
    )
    checkDuplicates(newComponents)
    copy(components = newComponents)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[ComponentDefinitionWithImplementation] =
    getComponent(ComponentId(componentType, componentName))

  def getComponent(id: ComponentId): Option[ComponentDefinitionWithImplementation] = {
    components.components.find(_.id == id)
  }

  def filterComponents(predicate: ComponentDefinitionWithImplementation => Boolean): ModelDefinition =
    copy(components = components.filter(predicate))

  private def checkDuplicates(components: Components): Unit = {

    def findDuplicates(values: List[ComponentDefinitionWithImplementation]) = {
      values
        .map(component => component.id -> component)
        .toGroupedMap
        .filter(_._2.size > 1)
        .keys
        .toList
        .sorted
    }

    val componentsDuplicates      = findDuplicates(components.components)
    val basicComponentsDuplicates = findDuplicates(components.basicComponents)

    if (componentsDuplicates.nonEmpty) {
      throw new DuplicatedComponentsException(componentsDuplicates)
    } else if (basicComponentsDuplicates.nonEmpty) {
      throw new DuplicatedComponentsException(basicComponentsDuplicates)
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
    new ModelDefinition(Components.empty, expressionConfig, settings).withComponents(components)

}
