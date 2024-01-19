package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

// TODO: This class should contain only parameters and returnType. For other things we should use ComponentDefinitionWithImplementation
final case class ComponentStaticDefinition(
    parameters: List[Parameter],
    returnType: Option[TypingResult],
    componentConfig: SingleComponentConfig,
    // This is necessary for sorting of components in the toolbox - see ComponentGroupsPreparer and notice next to sorting
    originalGroupName: ComponentGroupName,
    componentTypeSpecificData: ComponentTypeSpecificData,
) extends BaseComponentDefinition {

  def withComponentConfig(componentConfig: SingleComponentConfig): ComponentStaticDefinition =
    copy(componentConfig = componentConfig)

  val hasReturn: Boolean = returnType.isDefined

  override def componentType: ComponentType = componentTypeSpecificData.componentType

  // TODO: Instead of these methods we should replace componentConfig by configuration with well defined parameters
  def componentGroupUnsafe: ComponentGroupName =
    componentConfig.componentGroup.getOrElse(throw new IllegalStateException(s"Component group not defined in $this"))
  def iconUnsafe: String =
    componentConfig.icon.getOrElse(throw new IllegalStateException(s"Icon not defined in $this"))

  def componentIdUnsafe: ComponentId =
    componentConfig.componentId // relies on ComponentDefinitionExtractor and ModelDefinitionEnricher to provide components with filled componentConfig.componentId
      .getOrElse(
        throw new IllegalStateException(s"ComponentId not defined in $this")
      )

}
