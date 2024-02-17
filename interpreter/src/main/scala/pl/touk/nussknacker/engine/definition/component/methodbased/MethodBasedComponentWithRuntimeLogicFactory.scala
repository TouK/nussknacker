package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component._

final case class MethodBasedComponentWithRuntimeLogicFactory(
    override val name: String,
    override val runtimeLogicFactory: ComponentRuntimeLogicFactory,
    override val component: Component,
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    staticDefinition: ComponentStaticDefinition,
    override protected val uiDefinition: ComponentUiDefinition,
) extends ComponentWithRuntimeLogicFactory {

  override def withRuntimeLogicFactory(
      implementationInvoker: ComponentRuntimeLogicFactory
  ): ComponentWithRuntimeLogicFactory =
    copy(runtimeLogicFactory = implementationInvoker)

  def parameters: List[Parameter] = staticDefinition.parameters

  def returnType: Option[TypingResult] = staticDefinition.returnType

  override protected def typesFromStaticDefinition: List[TypingResult] = {
    def typesFromParameter(parameter: Parameter): List[TypingResult] = {
      val fromAdditionalVars = parameter.additionalVariables.values.map(_.typingResult)
      fromAdditionalVars.toList :+ parameter.typ
    }
    parameters.flatMap(typesFromParameter) ++ returnType
  }

}

object MethodBasedComponentWithRuntimeLogicFactory {

  def withNullRuntimeLogic(
      name: String,
      componentTypeSpecificData: ComponentTypeSpecificData,
      staticDefinition: ComponentStaticDefinition,
      uiDefinition: ComponentUiDefinition,
      allowedProcessingModes: Option[Set[ProcessingMode]],
  ): MethodBasedComponentWithRuntimeLogicFactory = {
    MethodBasedComponentWithRuntimeLogicFactory(
      name,
      ComponentRuntimeLogicFactory.nullRuntimeLogicFactory,
      new NullComponent(allowedProcessingModes),
      componentTypeSpecificData,
      staticDefinition,
      uiDefinition
    )
  }

  private class NullComponent(override val allowedProcessingModes: Option[Set[ProcessingMode]]) extends Component

}
