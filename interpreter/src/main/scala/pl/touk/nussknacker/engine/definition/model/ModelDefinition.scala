package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.{ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.component.ComponentIdProvider
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition

case class ModelDefinition[T](
    services: Map[String, T],
    sourceFactories: Map[String, T],
    sinkFactories: Map[String, T],
    // TODO: find easier way to handle *AdditionalData?
    customStreamTransformers: Map[String, (T, CustomTransformerAdditionalData)],
    expressionConfig: ExpressionDefinition[T],
    settings: ClassExtractionSettings
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  val componentNames: List[String] = {
    val names = services.keys ++
      sourceFactories.keys ++
      sinkFactories.keys ++
      customStreamTransformers.keys

    names.toList
  }

  private def servicesWithIds(
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): List[(ComponentIdWithName, T)] =
    services.map { case (name, obj) =>
      val id = componentIdProvider.createComponentId(
        processingType,
        ComponentInfo(ComponentType.Service, name)
      )
      ComponentIdWithName(id, name) -> obj
    }.toList

  private def customStreamTransformersWithIds(
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): List[(ComponentIdWithName, (T, CustomTransformerAdditionalData))] =
    customStreamTransformers.map { case (name, obj) =>
      val id = componentIdProvider.createComponentId(
        processingType,
        ComponentInfo(ComponentType.CustomComponent, name)
      )
      ComponentIdWithName(id, name) -> obj
    }.toList

  private def sinkFactoriesWithIds(
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): List[(ComponentIdWithName, T)] =
    sinkFactories.map { case (name, obj) =>
      val id = componentIdProvider.createComponentId(
        processingType,
        ComponentInfo(ComponentType.Sink, name)
      )
      ComponentIdWithName(id, name) -> obj
    }.toList

  private def sourceFactoriesWithIds(
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): List[(ComponentIdWithName, T)] =
    sourceFactories.map { case (name, obj) =>
      val id = componentIdProvider.createComponentId(
        processingType,
        ComponentInfo(ComponentType.Source, name)
      )
      ComponentIdWithName(id, name) -> obj
    }.toList

  def withComponentIds(
      componentIdProvider: ComponentIdProvider,
      processingType: String
  ): ModelDefinitionWithComponentIds[T] =
    ModelDefinitionWithComponentIds(
      servicesWithIds(componentIdProvider, processingType),
      sourceFactoriesWithIds(componentIdProvider, processingType),
      sinkFactoriesWithIds(componentIdProvider, processingType),
      customStreamTransformersWithIds(componentIdProvider, processingType),
      expressionConfig,
      settings
    )

  val allDefinitions: Map[String, T] =
    services ++ sourceFactories ++ sinkFactories ++ customStreamTransformers.mapValuesNow(_._1)

  def filter(predicate: T => Boolean): ModelDefinition[T] = copy(
    services.filter(kv => predicate(kv._2)),
    sourceFactories.filter(kv => predicate(kv._2)),
    sinkFactories.filter(kv => predicate(kv._2)),
    customStreamTransformers.filter(ct => predicate(ct._2._1)),
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.filter(kv => predicate(kv._2)))
  )

  def transform[R](f: T => R): ModelDefinition[R] = copy(
    services.mapValuesNow(f),
    sourceFactories.mapValuesNow(f),
    sinkFactories.mapValuesNow(f),
    customStreamTransformers.mapValuesNow { case (o, additionalData) => (f(o), additionalData) },
    expressionConfig.copy(globalVariables = expressionConfig.globalVariables.mapValuesNow(f))
  )

}

case class CustomTransformerAdditionalData(manyInputs: Boolean, canBeEnding: Boolean)
