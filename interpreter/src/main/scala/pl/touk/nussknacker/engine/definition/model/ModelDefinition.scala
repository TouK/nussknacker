package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentInfo
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.{BaseComponentDefinition, ComponentIdProvider}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition.checkDuplicates
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList

case class ModelDefinition[T <: BaseComponentDefinition] private (
    components: Map[ComponentInfo, T],
    expressionConfig: ExpressionDefinition[T],
    settings: ClassExtractionSettings
) {

  def addComponent(componentName: String, component: T): ModelDefinition[T] = {
    addComponents(List(ComponentInfo(component.componentType, componentName) -> component))
  }

  def addComponents(componentsToAdd: List[(ComponentInfo, T)]): ModelDefinition[T] = {
    val newComponents = components.toList ++ componentsToAdd
    checkDuplicates(newComponents)
    copy(components = newComponents.toMap)
  }

  def getComponent(componentType: ComponentType, componentName: String): Option[T] =
    components.get(ComponentInfo(componentType, componentName))

  import pl.touk.nussknacker.engine.util.Implicits._

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

  def filter(predicate: T => Boolean): ModelDefinition[T] = copy(
    components.filter(kv => predicate(kv._2)),
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.filter(kv => predicate(kv._2)))
  )

  def transform[R <: BaseComponentDefinition](f: T => R): ModelDefinition[R] = copy(
    components.mapValuesNow(f),
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.mapValuesNow(f))
  )

}

object ModelDefinition {

  def apply[T <: BaseComponentDefinition](
      components: List[(ComponentInfo, T)],
      expressionConfig: ExpressionDefinition[T],
      settings: ClassExtractionSettings
  ): ModelDefinition[T] = {
    checkDuplicates(components)
    new ModelDefinition(components.toMap, expressionConfig, settings)
  }

  private def checkDuplicates(components: List[(ComponentInfo, _)]): Unit = {
    val duplicates = components.toGroupedMap
      .filter(_._2.size > 1)
      .keys
      .map(_.toString)
      .toList
      .sorted
    if (duplicates.nonEmpty) {
      throw new IllegalArgumentException(
        s"Found duplicated components: ${duplicates.mkString(", ")}. Please correct model configuration."
      )
    }
  }

}
