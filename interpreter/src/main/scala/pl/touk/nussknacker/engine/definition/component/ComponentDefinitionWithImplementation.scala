package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{Component, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

// This class represents component's definition and implementation.
// Implementation part is in implementation field. It should be rarely used - instead we should extract information
// into definition. Implementation should be mainly used via implementationInvoker which can be transformed
// (e.g.) for purpose of stubbing.
// TODO: This class currently is used also for global variables. We should rather extract some other class for them
trait ComponentDefinitionWithImplementation {

  // TODO: It should be exposed only for real components - not for global variables
  def implementationInvoker: ComponentImplementationInvoker

  // For purpose of transforming (e.g.) stubbing of the implementation
  def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation

  // In  could be of type Component
  def implementation: Any

  // TODO: it should be available only for MethodBasedComponentDefinitionWithImplementation
  def returnType: Option[TypingResult]

  protected[definition] def categories: Option[List[String]]

  def availableForCategory(category: String): Boolean = categories.isEmpty || categories.exists(_.contains(category))

  def componentConfig: SingleComponentConfig

}

object ComponentDefinitionWithImplementation {

  import cats.syntax.semigroup._

  def forMap[T <: Component](
      objs: Map[String, WithCategories[_ <: T]],
      externalConfig: Map[String, SingleComponentConfig]
  ): Map[String, ComponentDefinitionWithImplementation] = {
    objs
      .map { case (id, obj) =>
        val config = externalConfig.getOrElse(id, SingleComponentConfig.zero) |+| obj.componentConfig
        id -> (obj, config)
      }
      .collect {
        case (id, (obj, config)) if !config.disabled =>
          id -> ComponentDefinitionExtractor.extract[T](obj, config)
      }
  }

  def withEmptyConfig[T <: Component](
      obj: T
  ): ComponentDefinitionWithImplementation =
    ComponentDefinitionExtractor.extract[T](WithCategories.anyCategory(obj), SingleComponentConfig.zero)

}
