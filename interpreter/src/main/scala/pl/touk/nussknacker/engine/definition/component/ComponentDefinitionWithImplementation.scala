package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition}
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

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

  def componentTypeSpecificData: ComponentTypeSpecificData

}

object ComponentDefinitionWithImplementation {

  def forList(
      components: List[ComponentDefinition],
      additionalConfigs: ComponentsUiConfig
  ): List[(String, ComponentDefinitionWithImplementation)] = {
    components.flatMap { component =>
      val config = additionalConfigs.getConfigByComponentName(component.name)
      if (config.disabled)
        None
      else
        Some(ComponentDefinitionExtractor.extract(component, config))
    }
  }

  def withEmptyConfig(obj: Component): ComponentDefinitionWithImplementation =
    ComponentDefinitionExtractor.extract(WithCategories.anyCategory(obj))

}
