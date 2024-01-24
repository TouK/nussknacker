package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component._

final case class MethodBasedComponentDefinitionWithImplementation(
    override val implementationInvoker: ComponentImplementationInvoker,
    override val implementation: Component,
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    staticDefinition: ComponentStaticDefinition,
    override protected val uiDefinition: ComponentUiDefinition,
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

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

object MethodBasedComponentDefinitionWithImplementation {

  def withNullImplementation(
      componentTypeSpecificData: ComponentTypeSpecificData,
      staticDefinition: ComponentStaticDefinition,
      uiDefinition: ComponentUiDefinition
  ): MethodBasedComponentDefinitionWithImplementation = {
    MethodBasedComponentDefinitionWithImplementation(
      ComponentImplementationInvoker.nullImplementationInvoker,
      null,
      componentTypeSpecificData,
      staticDefinition,
      uiDefinition
    )
  }

}
