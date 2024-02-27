package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.WithExplicitTypesToExtract
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

// This class is our domain model class for the Component. It is used on the designer side for definitions
// served to the FE and for validations. It is used on the runtime side for component's runtime logic execution and for stubbing.
// Implementing component is in the component field. It should be rarely used - instead, we should extract information
// into definition. Runtime logic should be used via runtimeLogicFactory which can be transformed
// (e.g.) for purpose of stubbing.
trait ComponentWithDefinition extends ObjectOperatingOnTypes {

  def runtimeLogicFactory: ComponentRuntimeLogicFactory

  // For purpose of transforming (e.g.) stubbing of the runtime logic
  def withRuntimeLogicFactory(runtimeLogicFactory: ComponentRuntimeLogicFactory): ComponentWithDefinition

  def component: Component

  def componentTypeSpecificData: ComponentTypeSpecificData

  // This field is used as a part of identifier so it is important that it should be stable.
  // Currently it is used also for a label presented at the toolbox palette but we should probably extract another
  // field (e.g. label) that will be in a more human friendly format`- see Parameter.name vs Parameter.label
  def name: String

  final def componentType: ComponentType = componentTypeSpecificData.componentType

  final def id: ComponentId = ComponentId(componentType, name)

  protected def uiDefinition: ComponentUiDefinition

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

object ComponentWithDefinition {

  def forList(
      components: List[ComponentDefinition],
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): List[ComponentWithDefinition] = {
    components.flatMap(
      ComponentDefinitionExtractor.extract(_, additionalConfigs, determineDesignerWideId, additionalConfigsFromProvider)
    )
  }

  /*  This method is mainly for the tests purpose. It doesn't take into an account:
   *    - additionalConfigs from the model configuration
   *    - additionalConfigsFromProvider provided by AdditionalUIConfigProvider
   */
  def withEmptyConfig(name: String, component: Component): ComponentWithDefinition = {
    ComponentDefinitionExtractor
      .extract(
        name,
        component,
        SingleComponentConfig.zero,
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
