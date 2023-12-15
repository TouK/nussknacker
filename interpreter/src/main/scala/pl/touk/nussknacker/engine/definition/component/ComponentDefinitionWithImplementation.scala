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

  def implementationInvoker: ComponentImplementationInvoker

  // For purpose of transforming (e.g.) stubbing of the implementation
  def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation

  // TODO In should be of type Component, but currently this class is used also for global variables
  def implementation: Any

  // TODO: it should be only available for MethodBasedComponentDefinitionWithImplementation for purpose of collecting
  //       ClassDefinition, but currently it is also served to FE - see a comment next to UIComponentDefinition
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
