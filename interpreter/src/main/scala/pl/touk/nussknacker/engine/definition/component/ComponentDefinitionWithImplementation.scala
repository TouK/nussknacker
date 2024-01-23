package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

// This class represents component's definition and implementation. It is used on the designer side for definitions used
// in the GUI, and for validations. It is also used on the runtime side for runtime and for stubbed runtime.
// Implementation part is in implementation field. It should be rarely used - instead we should extract information
// into definition. Implementation should be mainly used via implementationInvoker which can be transformed
// (e.g.) for purpose of stubbing.
// TODO: This class currently is used also for global variables and fragments. We should rather extract some other class for them
trait ComponentDefinitionWithImplementation {

  def implementationInvoker: ComponentImplementationInvoker

  // For purpose of transforming (e.g.) stubbing of the implementation
  def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation

  // TODO In should be of Component type, but currently this class is used also for global variables and fragments
  def implementation: Any

  def componentTypeSpecificData: ComponentTypeSpecificData

  final def componentType: ComponentType = componentTypeSpecificData.componentType

  protected def uiDefinition: ComponentUiDefinition

  final def componentId: ComponentId = uiDefinition.componentId

  final def componentGroup: ComponentGroupName = uiDefinition.componentGroup

  final def originalGroupName: ComponentGroupName = uiDefinition.originalGroupName

  final def icon: String = uiDefinition.icon

  final def docsUrl: Option[String] = uiDefinition.docsUrl

}

final case class ComponentUiDefinition(
    originalGroupName: ComponentGroupName,
    componentGroup: ComponentGroupName,
    icon: String,
    docsUrl: Option[String],
    componentId: ComponentId
)

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
