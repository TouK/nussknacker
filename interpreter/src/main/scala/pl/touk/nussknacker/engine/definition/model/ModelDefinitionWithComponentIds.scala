package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

case class ModelDefinitionWithComponentIds[T](
    components: List[(ComponentIdWithName, T)],
    expressionConfig: ExpressionDefinition[T],
    settings: ClassExtractionSettings
) {

  def filter(predicate: T => Boolean): ModelDefinitionWithComponentIds[T] = copy(
    components.filter(kv => predicate(kv._2)),
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.filter(kv => predicate(kv._2)))
  )

  def transform[R](f: T => R): ModelDefinitionWithComponentIds[R] = copy(
    components.map { case (idWithName, component) => (idWithName, f(component)) },
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.mapValuesNow(f))
  )

}

case class ComponentIdWithName(id: ComponentId, name: String)
