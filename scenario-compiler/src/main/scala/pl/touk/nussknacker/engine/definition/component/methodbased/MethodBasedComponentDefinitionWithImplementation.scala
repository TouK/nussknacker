package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.{Component, ComponentGroupName}
import pl.touk.nussknacker.engine.api.component.Component._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.util.NotNothing
import pl.touk.nussknacker.engine.definition.component._

import scala.reflect.ClassTag

final case class MethodBasedComponentDefinitionWithImplementation(
    override val name: String,
    override val implementationInvoker: ComponentImplementationInvoker,
    override val component: Component,
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

  def withDumbImplementation[ComponentExecutor: NotNothing: ClassTag](
      name: String,
      componentTypeSpecificData: ComponentTypeSpecificData,
      staticDefinition: ComponentStaticDefinition,
      uiDefinition: ComponentUiDefinition,
      allowedProcessingModes: AllowedProcessingModes,
  ): MethodBasedComponentDefinitionWithImplementation = {
    MethodBasedComponentDefinitionWithImplementation(
      name = name,
      implementationInvoker = ComponentImplementationInvoker.dumbImplementationInvoker[ComponentExecutor],
      component = new FakeComponentWithAllowedProcessingModesSpecified(allowedProcessingModes),
      componentTypeSpecificData = componentTypeSpecificData,
      staticDefinition = staticDefinition,
      uiDefinition = uiDefinition
    )
  }

  class FakeComponentWithAllowedProcessingModesSpecified(override val allowedProcessingModes: AllowedProcessingModes)
      extends Component

}
