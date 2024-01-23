package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  ComponentUiDefinition,
  GlobalVariablesSpecificData
}

object GlobalVariableDefinitionExtractor {

  def extractDefinition(variable: AnyRef): MethodBasedComponentDefinitionWithImplementation = {
    val returnType = variable match {
      case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.initialReturnType
      case obj                                      => Typed.fromInstance(obj)
    }

    MethodBasedComponentDefinitionWithImplementation(
      // Global variables are always accessed by MethodBasedComponentDefinitionWithImplementation.obj - see GlobalVariablesPreparer
      // and comment in ComponentDefinitionWithImplementation.implementationInvoker
      ComponentImplementationInvoker.nullImplementationInvoker,
      variable,
      GlobalVariablesSpecificData,
      ComponentStaticDefinition(List.empty, Some(returnType)),
      ComponentUiDefinition(
        originalGroupName = ComponentGroupName("dumbGroup"),
        componentGroup = ComponentGroupName("dumbGroup"),
        icon = "dumpIcon",
        docsUrl = None,
        componentId = ComponentId("dumbId")
      )
    )
  }

}
