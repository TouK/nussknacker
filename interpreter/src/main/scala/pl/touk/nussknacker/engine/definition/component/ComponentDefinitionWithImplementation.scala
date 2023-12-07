package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component.implinvoker.ComponentImplementationInvoker
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodDefinitionExtractor

// This class represents component's definition and implementation.
// Implementation part is in implementation field. It should be rarely used - instead we should extract information
// into definition. Implementation should be mainly used via implementationInvoker which can be transformed
// (e.g.) for purpose of stubbing.
// TODO: This class currently is used also for global variables. We should rather extract some other class for them
sealed trait ComponentDefinitionWithImplementation {

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

  def forMap[T](
      objs: Map[String, WithCategories[_ <: T]],
      methodExtractor: MethodDefinitionExtractor[T],
      externalConfig: Map[String, SingleComponentConfig]
  ): Map[String, ComponentDefinitionWithImplementation] = {
    objs
      .map { case (id, obj) =>
        val config = externalConfig.getOrElse(id, SingleComponentConfig.zero) |+| obj.componentConfig
        id -> (obj, config)
      }
      .collect {
        case (id, (obj, config)) if !config.disabled =>
          id -> new ComponentDefinitionExtractor(methodExtractor).extract(obj, config)
      }
  }

  def withEmptyConfig[T](
      obj: T,
      methodExtractor: MethodDefinitionExtractor[T]
  ): ComponentDefinitionWithImplementation = {
    new ComponentDefinitionExtractor(methodExtractor)
      .extract(WithCategories.anyCategory(obj), SingleComponentConfig.zero)
  }

}

case class DynamicComponentDefinitionWithImplementation(
    override val implementationInvoker: ComponentImplementationInvoker,
    implementation: GenericNodeTransformation[_],
    override protected[definition] val categories: Option[List[String]],
    override val componentConfig: SingleComponentConfig
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  def returnType: Option[TypingResult] =
    if (implementation.nodeDependencies.contains(OutputVariableNameDependency)) Some(Unknown) else None

}

case class MethodBasedComponentDefinitionWithImplementation(
    implementationInvoker: ComponentImplementationInvoker,
    implementation: Any,
    objectDefinition: ComponentStaticDefinition,
    // TODO: it should be removed - instead implementationInvoker should be transformed
    runtimeClass: Class[_]
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  def parameters: List[Parameter] = objectDefinition.parameters

  override def returnType: Option[TypingResult] = objectDefinition.returnType

  override protected[definition] def categories: Option[List[String]] = objectDefinition.categories

  override def componentConfig: SingleComponentConfig = objectDefinition.componentConfig

}
