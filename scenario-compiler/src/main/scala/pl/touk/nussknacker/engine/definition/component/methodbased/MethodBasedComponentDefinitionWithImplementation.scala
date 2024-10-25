package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.component.Component._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component._

final case class MethodBasedComponentDefinitionWithImplementation(
    override val name: String,
    override val implementationInvoker: ComponentImplementationInvoker,
    override val component: Component,
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    staticDefinition: ComponentStaticDefinition,
    override protected val uiDefinitions: ComponentUiDefinitions,
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
      uiDefinitions: ComponentUiDefinitions,
      allowedProcessingModes: AllowedProcessingModes,
  ): MethodBasedComponentDefinitionWithImplementation = {
    MethodBasedComponentDefinitionWithImplementation(
      name,
      ComponentImplementationInvoker.nullReturningComponentImplementationInvoker,
      new FakeComponentWithAllowedProcessingModesSpecified(allowedProcessingModes),
      componentTypeSpecificData,
      staticDefinition,
      uiDefinitions
    )
  }

  class FakeComponentWithAllowedProcessingModesSpecified(override val allowedProcessingModes: AllowedProcessingModes)
      extends Component

}
