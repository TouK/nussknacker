package pl.touk.nussknacker.engine.definition.component.methodbased

import cats.data.NonEmptySet
import pl.touk.nussknacker.engine.api.component.ProcessingMode.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component._

final case class MethodBasedComponentDefinitionWithImplementation(
    override val name: String,
    override val implementationInvoker: ComponentImplementationInvoker,
    override val implementation: Component,
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    staticDefinition: ComponentStaticDefinition,
    override protected val uiDefinition: ComponentUiDefinition,
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      invoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = invoker)

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
      name: String,
      componentTypeSpecificData: ComponentTypeSpecificData,
      staticDefinition: ComponentStaticDefinition,
      uiDefinition: ComponentUiDefinition,
      allowedProcessingModes: AllowedProcessingModes,
  ): MethodBasedComponentDefinitionWithImplementation = {
    MethodBasedComponentDefinitionWithImplementation(
      name,
      ComponentImplementationInvoker.nullReturningComponentImplementationInvoker,
      new NullComponent(allowedProcessingModes),
      componentTypeSpecificData,
      staticDefinition,
      uiDefinition
    )
  }

  private class NullComponent(override val allowedProcessingModes: AllowedProcessingModes) extends Component

}
