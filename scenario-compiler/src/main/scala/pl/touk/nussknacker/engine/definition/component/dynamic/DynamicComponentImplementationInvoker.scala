package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.{MetaData, NodeId, NodeName, Params}
import pl.touk.nussknacker.engine.api.context.transformation.{
  DynamicComponent,
  OutputVariableNameValue,
  TypedNodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.compile.nodecompilation.ImplicitSourceOutputVariableHandler.NodeDataExt
import pl.touk.nussknacker.engine.definition.component.{ComponentImplementationInvoker, NodeCompilationDependencies}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.{
  ComponentImplementationSpecificInvocationContext,
  DynamicComponentInvocationContext
}

class DynamicComponentImplementationInvoker(obj: DynamicComponent) extends ComponentImplementationInvoker {

  override def invokeMethod(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any = {
    // TODO: pass typed NodeCompilationDependencies to implementation instead of additionalParams
    val additionalParams = obj.nodeDependencies.map {
      case TypedNodeDependency(klazz) if klazz == classOf[NodeId] =>
        TypedNodeDependencyValue(compilationDependencies.nodeId)
      case TypedNodeDependency(klazz) if klazz == classOf[NodeName] =>
        TypedNodeDependencyValue(compilationDependencies.nodeName)
      case TypedNodeDependency(klazz) if klazz == classOf[MetaData] =>
        TypedNodeDependencyValue(compilationDependencies.metaData)
      case TypedNodeDependency(klazz) if klazz == classOf[ComponentUseContext] =>
        TypedNodeDependencyValue(compilationDependencies.componentUseContext)
      case TypedNodeDependency(klazz) =>
        compilationDependencies.engineScenarioCompilationDependencies.nodeCompilationDependencies
          .collectFirst {
            case typedValue @ TypedNodeDependencyValue(value) if klazz.isInstance(value) => typedValue
          }
          .getOrElse(throw new IllegalArgumentException(s"Failed to find dependency: $klazz"))
      case OutputVariableNameDependency =>
        compilationDependencies.nodeData.outputVariableNameHandlingInputSourceVariableName
          .map(OutputVariableNameValue)
          .getOrElse(throw new IllegalArgumentException("Output variable not defined"))
      case other => throw new IllegalArgumentException(s"Cannot handle dependency $other")
    }
    val rawFinalStateValue = invocationContext match {
      case Some(DynamicComponentInvocationContext(finalStateValue, _)) =>
        finalStateValue.value.asInstanceOf[Option[obj.State]]
      case other => throw new IllegalArgumentException(s"Illegal implementation specific context: $other")
    }
    // we assume parameters were already validated!
    obj.implementation(params, additionalParams, rawFinalStateValue)
  }

}

case class FinalStateValue(value: Option[Any])
