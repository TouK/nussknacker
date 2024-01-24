package pl.touk.nussknacker.engine.definition.component

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
  def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation

  def implementation: Component

  def componentTypeSpecificData: ComponentTypeSpecificData

  final def componentType: ComponentType = componentTypeSpecificData.componentType

  protected def uiDefinition: ComponentUiDefinition

  final def designerWideId: DesignerWideComponentId = uiDefinition.designerWideId

  final def componentGroup: ComponentGroupName = uiDefinition.componentGroup

  final def originalGroupName: ComponentGroupName = uiDefinition.originalGroupName

  final def icon: String = uiDefinition.icon

  final def docsUrl: Option[String] = uiDefinition.docsUrl

  override final def definedTypes: List[TypingResult] = {
    val fromExplicitTypes = implementation match {
      case explicit: WithExplicitTypesToExtract => explicit.typesToExtract
      case _                                    => Nil
    }
    typesFromStaticDefinition ++ fromExplicitTypes
  }

  protected def typesFromStaticDefinition: List[TypingResult]

}

trait ObjectOperatingOnTypes {

  def definedTypes: List[TypingResult]

}

final case class ComponentUiDefinition(
    originalGroupName: ComponentGroupName,
    componentGroup: ComponentGroupName,
    icon: String,
    docsUrl: Option[String],
    designerWideId: DesignerWideComponentId
)

object ComponentDefinitionWithImplementation {

  def forList(
      components: List[ComponentDefinition],
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): List[(String, ComponentDefinitionWithImplementation)] = {
    components.flatMap(
      ComponentDefinitionExtractor.extract(_, additionalConfigs, determineDesignerWideId, additionalConfigsFromProvider)
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
        id => DesignerWideComponentId(id.toString),
        Map.empty
      )
      .getOrElse(
        throw new IllegalStateException(
          s"ComponentDefinitionWithImplementation.withEmptyConfig returned None for: $component but component should be filtered for empty config"
        )
      )
  }

}
