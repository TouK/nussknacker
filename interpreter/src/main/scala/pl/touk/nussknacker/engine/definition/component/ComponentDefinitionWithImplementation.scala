package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component._
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

  def componentTypeSpecificData: ComponentTypeSpecificData

}

object ComponentDefinitionWithImplementation {

  def forList(
      components: List[ComponentDefinition],
      additionalConfigs: ComponentsUiConfig,
      componentInfoToId: ComponentInfo => ComponentId,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
  ): List[(String, ComponentDefinitionWithImplementation)] = {
    components.flatMap(
      ComponentDefinitionExtractor.extract(_, additionalConfigs, componentInfoToId, additionalConfigsFromProvider)
    )
  }

  /*  This method is mainly for the tests purpose. It doesn't take into an account:
   *    - additionalConfigs from the model configuration
   *    - additionalConfigsFromProvider provided by AdditionalUIConfigProvider
   */
  def withEmptyConfig(component: Component): ComponentDefinitionWithImplementation = {
    ComponentDefinitionExtractor
      .extract(
        "dumbName",
        component,
        SingleComponentConfig.zero,
        ComponentsUiConfig.Empty,
        info => ComponentId(info.toString),
        Map.empty
      )
      .getOrElse(
        throw new IllegalStateException(
          s"ComponentDefinitionWithImplementation.withEmptyConfig returned None for: $component but component should be filtered for empty config"
        )
      )
  }

}
