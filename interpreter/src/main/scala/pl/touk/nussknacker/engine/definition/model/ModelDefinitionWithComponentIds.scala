package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

case class ModelDefinitionWithComponentIds[T](
    services: List[(ComponentIdWithName, T)],
    sourceFactories: List[(ComponentIdWithName, T)],
    sinkFactories: List[(ComponentIdWithName, T)],
    customStreamTransformers: List[(ComponentIdWithName, T)],
    expressionConfig: ExpressionDefinition[T],
    settings: ClassExtractionSettings
) {

  val allDefinitions: List[(ComponentIdWithName, T)] =
    services ++ sourceFactories ++ sinkFactories ++ customStreamTransformers

  def transform[R](f: T => R): ModelDefinitionWithComponentIds[R] = copy(
    services.map { case (idWithName, value) => (idWithName, f(value)) },
    sourceFactories.map { case (idWithName, value) => (idWithName, f(value)) },
    sinkFactories.map { case (idWithName, value) => (idWithName, f(value)) },
    customStreamTransformers.map { case (idWithName, value) => (idWithName, f(value)) },
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.mapValuesNow(f))
  )

}

case class ComponentIdWithName(id: ComponentId, name: String)
