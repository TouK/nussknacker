package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.compile.nodecompilation.ImplicitSourceOutputVariableHandler.NodeDataExt
import pl.touk.nussknacker.engine.definition.component.{ComponentImplementationInvoker, NodeCompilationDependencies}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.{
  ComponentImplementationSpecificInvocationContext,
  DynamicComponentInvocationContext,
  LazyServiceInvocationContext
}

private[definition] class MethodBasedComponentImplementationInvoker(
    obj: Any,
    private[definition] val methodDef: MethodDefinition
) extends ComponentImplementationInvoker
    with LazyLogging {

  override def invokeMethod(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any = {
    val componentSpecificAdditional = invocationContext
      .map {
        case dynamicContext: DynamicComponentInvocationContext =>
          throw new IllegalStateException(s"$dynamicContext used for method-based components")
        case LazyServiceInvocationContext(executionContext, collector, variablesContext) =>
          List(executionContext, collector, variablesContext, variablesContext.id)
      }
      .toList
      .flatten
    val additional =
      compilationDependencies.metaData ::
        compilationDependencies.nodeId ::
        compilationDependencies.nodeName ::
        compilationDependencies.componentUseContext ::
        compilationDependencies.engineScenarioCompilationDependencies.nodeCompilationDependencies.map(_.value) :::
        componentSpecificAdditional
    methodDef.invoke(
      obj,
      params.nameToRawValueMap,
      compilationDependencies.nodeData.outputVariableNameHandlingInputSourceVariableName,
      additional
    )
  }

}
