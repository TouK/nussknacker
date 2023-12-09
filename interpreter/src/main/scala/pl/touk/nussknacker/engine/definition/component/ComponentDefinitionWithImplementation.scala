package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentInfo, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

// This class represents component's definition and implementation.
// Implementation part is in implementation field. It should be rarely used - instead we should extract information
// into definition. Implementation should be mainly used via implementationInvoker which can be transformed
// (e.g.) for purpose of stubbing.
// TODO: This class currently is used also for global variables. We should rather extract some other class for them
trait ComponentDefinitionWithImplementation extends BaseComponentDefinition {

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

  // TODO: remove from here
  protected[definition] def categories: Option[List[String]]

  def availableForCategory(category: String): Boolean = categories.isEmpty || categories.exists(_.contains(category))

  def componentConfig: SingleComponentConfig

  def componentTypeSpecificData: ComponentTypeSpecificData

}

object ComponentDefinitionWithImplementation {

  import cats.syntax.semigroup._

  // TODO: Move this WithCategories extraction to ModelDefinitionFromConfigCreatorExtractor, remove category from
  //       ComponentDefinitionWithImplementation
  def forList(
      components: List[(String, WithCategories[Component])],
      externalConfig: Map[String, SingleComponentConfig]
  ): List[(String, ComponentDefinitionWithImplementation)] = {
    components
      .map { case (componentName, component) =>
        val config = externalConfig.getOrElse(componentName, SingleComponentConfig.zero) |+| component.componentConfig
        componentName -> (component, config)
      }
      .collect {
        case (componentName, (component, config)) if !config.disabled =>
          val componentDefWithImpl = ComponentDefinitionExtractor.extract(component.withComponentConfig(config))
          componentName -> componentDefWithImpl
      }
  }

  def withEmptyConfig(obj: Component): ComponentDefinitionWithImplementation =
    ComponentDefinitionExtractor.extract(WithCategories.anyCategory(obj))

}
