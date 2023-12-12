package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation
import pl.touk.nussknacker.engine.api.definition.OutputVariableNameDependency
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentTypeSpecificData
}

final case class DynamicComponentDefinitionWithImplementation(
    override val implementationInvoker: ComponentImplementationInvoker,
    implementation: GenericNodeTransformation[_],
    private[component] val categories: Option[List[String]],
    componentConfig: SingleComponentConfig,
    override val componentTypeSpecificData: ComponentTypeSpecificData
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  def returnType: Option[TypingResult] =
    if (implementation.nodeDependencies.contains(OutputVariableNameDependency)) Some(Unknown) else None

  override def componentType: ComponentType = componentTypeSpecificData.componentType

}
