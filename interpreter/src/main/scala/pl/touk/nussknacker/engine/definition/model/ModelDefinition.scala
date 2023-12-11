package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentInfo
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.{BaseComponentDefinition, ComponentIdProvider}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition

case class ModelDefinition[T <: BaseComponentDefinition] private (
    components: Map[ComponentInfo, T],
    expressionConfig: ExpressionDefinition[T],
    settings: ClassExtractionSettings
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  def withComponent(componentName: String, component: T): ModelDefinition[T] = {
    withComponents(List(componentName -> component))
  }

  def withComponents(componentsToAdd: List[(String, T)]): ModelDefinition[T] = {
    val newComponents = components.toList ++ componentsToAdd.map { case (componentName, component) =>
      ComponentInfo(component.componentType, componentName) -> component
    }
    checkDuplicates(newComponents)
    copy(components = newComponents.toMap)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[T] =
    components.get(ComponentInfo(componentType, componentName))

  // FIXME: remove from here and move ComponentIdProvider outside of interpreter
  def withComponentIds(
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): ModelDefinitionWithComponentIds[T] = {
    val transformedComponents =
      components.toList.map { case (info, component) =>
        val id = componentIdProvider.createComponentId(processingType, info)
        ComponentIdWithName(id, info.name) -> component
      }
    ModelDefinitionWithComponentIds(
      transformedComponents,
      expressionConfig,
      settings
    )
  }

  def transform[R <: BaseComponentDefinition](f: T => R): ModelDefinition[R] = copy(
    components.mapValuesNow(f),
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.mapValuesNow(f))
  )

  private def checkDuplicates(components: List[(ComponentInfo, T)]): Unit = {
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

  def apply[T <: BaseComponentDefinition](
      components: List[(String, T)],
      expressionConfig: ExpressionDefinition[T],
      settings: ClassExtractionSettings
  ): ModelDefinition[T] =
    new ModelDefinition(Map.empty, expressionConfig, settings).withComponents(components)

}
