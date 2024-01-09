package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  GlobalVariablesSpecificData
}

object GlobalVariableDefinitionExtractor {

  def extractDefinition(
      variable: AnyRef,
      categories: Option[List[String]]
  ): MethodBasedComponentDefinitionWithImplementation = {
    val returnType = variable match {
      case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.initialReturnType
      case obj                                      => Typed.fromInstance(obj)
    }
    val staticDefinition = ComponentStaticDefinition(
      parameters = Nil,
      returnType = Some(returnType),
      categories = categories,
      componentConfig = SingleComponentConfig.zero,
      originalGroupName = ComponentGroupName("dumbGroup"),
      componentTypeSpecificData = GlobalVariablesSpecificData
    )
    MethodBasedComponentDefinitionWithImplementation(
      // Global variables are always accessed by MethodBasedComponentDefinitionWithImplementation.obj - see GlobalVariablesPreparer
      // and comment in ComponentDefinitionWithImplementation.implementationInvoker
      ComponentImplementationInvoker.nullImplementationInvoker,
      variable,
      staticDefinition
    )
  }

}
