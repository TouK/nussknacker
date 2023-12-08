package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  methodbased
}

object GlobalVariableDefinitionExtractor {

  import pl.touk.nussknacker.engine.util.Implicits._

  def extractDefinitions(
      objs: Map[String, WithCategories[AnyRef]]
  ): Map[String, ComponentDefinitionWithImplementation] = {
    objs.mapValuesNow(extractDefinition)
  }

  def extractDefinition(varWithCategories: WithCategories[AnyRef]): MethodBasedComponentDefinitionWithImplementation = {
    val returnType = varWithCategories.value match {
      case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.initialReturnType
      case obj                                      => Typed.fromInstance(obj)
    }
    val staticDefinition = ComponentStaticDefinition(
      componentType = ComponentType.BuiltIn,
      parameters = Nil,
      returnType = Some(returnType),
      categories = varWithCategories.categories,
      componentConfig = varWithCategories.componentConfig
    )
    MethodBasedComponentDefinitionWithImplementation(
      // Global variables are always accessed by MethodBasedComponentDefinitionWithImplementation.obj - see GlobalVariablesPreparer
      // and comment in ComponentDefinitionWithImplementation.implementationInvoker
      ComponentImplementationInvoker.nullImplementationInvoker,
      varWithCategories.value,
      staticDefinition,
      // Used only for services
      classOf[Any]
    )
  }

}
