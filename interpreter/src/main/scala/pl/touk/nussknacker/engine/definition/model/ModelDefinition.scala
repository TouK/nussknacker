package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentInfo
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.BaseComponentDefinition
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinition[T <: BaseComponentDefinition] private (
    components: Map[ComponentInfo, T],
    expressionConfig: ExpressionConfigDefinition[T],
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
    getComponent(ComponentInfo(componentType, componentName))

  def getComponent(info: ComponentInfo): Option[T] = {
    components.get(info)
  }

  def filterComponents(predicate: (ComponentInfo, T) => Boolean): ModelDefinition[T] = copy(
    components.filter(predicate tupled)
  )

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
      expressionConfig: ExpressionConfigDefinition[T],
      settings: ClassExtractionSettings
  ): ModelDefinition[T] =
    new ModelDefinition(Map.empty, expressionConfig, settings).withComponents(components)

}
