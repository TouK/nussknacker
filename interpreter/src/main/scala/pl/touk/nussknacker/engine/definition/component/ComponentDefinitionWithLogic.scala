package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, SingleComponentConfig}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

// This class represents component's definition and implementation.
// Implementation part is in implementation field. It should be rarely used - instead we should extract information
// into definition. Implementation should be mainly used via implementationInvoker which can be transformed
// (e.g.) for purpose of stubbing.
// TODO: This class currently is used also for global variables. We should rather extract some other class for them
trait ComponentDefinitionWithLogic extends BaseComponentDefinition {

  def componentLogic: ComponentLogic

  // For purpose of transforming (e.g.) stubbing of the implementation
  def withComponentLogic(
      implementationInvoker: ComponentLogic
  ): ComponentDefinitionWithLogic

  // TODO In should be of type Component, but currently this class is used also for global variables
  def component: Any

  def componentTypeSpecificData: ComponentTypeSpecificData

}

object ComponentDefinitionWithLogic {

  def forList(
      components: List[ComponentDefinition],
      additionalConfigs: ComponentsUiConfig
  ): List[(String, ComponentDefinitionWithLogic)] = {
    components.flatMap(ComponentDefinitionExtractor.extract(_, additionalConfigs))
  }

  // This method is mainly for the tests purpose. It doesn't take into an account additionalConfigs provided from the model configuration
  def withEmptyConfig(component: Component): ComponentDefinitionWithLogic = {
    ComponentDefinitionExtractor
      .extract("dumbName", component, SingleComponentConfig.zero, ComponentsUiConfig.Empty)
      .getOrElse(
        throw new IllegalStateException(
          s"ComponentDefinitionWithImplementation.withEmptyConfig returned None for: $component but component should be filtered for empty config"
        )
      )
  }

}
