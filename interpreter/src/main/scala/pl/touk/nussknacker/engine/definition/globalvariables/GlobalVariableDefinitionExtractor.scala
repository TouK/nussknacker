package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.{
  ComponentLogic,
  ComponentStaticDefinition,
  GlobalVariablesSpecificData
}

object GlobalVariableDefinitionExtractor {

  def extractDefinition(variable: AnyRef): MethodBasedComponentDefinitionWithLogic = {
    val returnType = variable match {
      case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.initialReturnType
      case obj                                      => Typed.fromInstance(obj)
    }
    val staticDefinition = ComponentStaticDefinition(
      parameters = Nil,
      returnType = Some(returnType),
      componentConfig = SingleComponentConfig.zero,
      originalGroupName = ComponentGroupName("dumbGroup"),
      componentTypeSpecificData = GlobalVariablesSpecificData
    )
    MethodBasedComponentDefinitionWithLogic(
      // Global variables are always accessed by MethodBasedComponentDefinitionWithImplementation.obj - see GlobalVariablesPreparer
      // and comment in ComponentDefinitionWithImplementation.implementationInvoker
      ComponentLogic.nullImplementationComponentLogic,
      variable,
      staticDefinition
    )
  }

}
