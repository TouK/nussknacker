package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

final case class ComponentStaticDefinition(
    parameters: List[Parameter],
    returnType: Option[TypingResult],
    // TODO: Remove it. We can take it from the processingType property, we don't to keep it here
    categories: Option[List[String]],
    componentConfig: SingleComponentConfig,
    componentTypeSpecificData: ComponentTypeSpecificData
) extends BaseComponentDefinition {

  def withComponentConfig(componentConfig: SingleComponentConfig): ComponentStaticDefinition =
    copy(componentConfig = componentConfig)

  val hasReturn: Boolean = returnType.isDefined

  def componentGroupUnsafe: ComponentGroupName =
    componentConfig.componentGroup.getOrElse(throw new IllegalStateException(s"Component group not defined for $this"))

  override def componentType: ComponentType = componentTypeSpecificData.componentType

}
