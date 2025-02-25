package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.Component._
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.WithExplicitTypesToExtract
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

// This class represents component's definition and implementation. It is used on the designer side for definitions
// served to the FE and for validations. It is used on the runtime side for component's runtime execution and for stubbing.
// Implementing component is in hte implementation field. It should be rarely used - instead, we should extract information
// into definition. Runtime logic should be mainly used via implementationInvoker which can be transformed
// (e.g.) for purpose of stubbing.
trait ComponentDefinitionWithImplementation extends ObjectOperatingOnTypes {

  def implementationInvoker: ComponentImplementationInvoker

  // For purpose of transforming (e.g.) stubbing of the implementation
  def withImplementationInvoker(invoker: ComponentImplementationInvoker): ComponentDefinitionWithImplementation

  def component: Component

  def componentTypeSpecificData: ComponentTypeSpecificData

  // This field is used as a part of identifier so it is important that it should be stable.
  def name: String

  final def componentType: ComponentType = componentTypeSpecificData.componentType

  final def id: ComponentId = ComponentId(componentType, name)

  protected def uiDefinition: ComponentUiDefinition

  final def label: String = {
    uiDefinition.label.getOrElse {
      if (componentType != ComponentType.Fragment) {
        name
      } else {
        id.name
      }
    }
  }

  final def designerWideId: DesignerWideComponentId = uiDefinition.designerWideId

  final def componentGroup: ComponentGroupName = uiDefinition.componentGroup

  final def originalGroupName: ComponentGroupName = uiDefinition.originalGroupName

  final def icon: String = uiDefinition.icon

  final def docsUrl: Option[String] = uiDefinition.docsUrl

  override final def definedTypes: List[TypingResult] = {
    val fromExplicitTypes = component match {
      case explicit: WithExplicitTypesToExtract => explicit.typesToExtract
      case _                                    => Nil
    }
    typesFromStaticDefinition ++ fromExplicitTypes
  }

  protected def typesFromStaticDefinition: List[TypingResult]

  def allowedProcessingModes: AllowedProcessingModes = component.allowedProcessingModes

}

trait ObjectOperatingOnTypes {

  def definedTypes: List[TypingResult]

}

final case class ComponentUiDefinition(
    originalGroupName: ComponentGroupName,
    componentGroup: ComponentGroupName,
    icon: String,
    docsUrl: Option[String],
    designerWideId: DesignerWideComponentId,
    label: Option[String]
)

object ComponentDefinitionWithImplementation {

  def forList(
      components: List[ComponentDefinition],
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): List[ComponentDefinitionWithImplementation] = {
    components.flatMap(
      ComponentDefinitionExtractor.extract(_, additionalConfigs, determineDesignerWideId, additionalConfigsFromProvider)
    )
  }

  /*  This method is mainly for the tests purpose. It doesn't take into an account:
   *    - additionalConfigs from the model configuration
   *    - additionalConfigsFromProvider provided by AdditionalUIConfigProvider
   */
  def withEmptyConfig(name: String, component: Component): ComponentDefinitionWithImplementation = {
    ComponentDefinitionExtractor
      .extract(
        name,
        component,
        ComponentConfig.zero,
        ComponentsUiConfig.Empty,
        id => DesignerWideComponentId(id.toString),
        Map.empty
      )
      .getOrElse(
        throw new IllegalStateException(
          s"Cannot extract component definition for $name (and component type: ${component.getClass.getName})"
        )
      )
  }

}
