package pl.touk.nussknacker.engine.compile

import cats.data.IorNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.ComponentExecutorFactory.ComponentExecutorDependencies
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, ParameterEvaluator}
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext

// This class helps to create an Executor using Component. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
class ComponentExecutorFactory(parameterEvaluator: ParameterEvaluator) extends LazyLogging {

  def createComponentExecutor[ComponentExecutor](
      deps: ComponentExecutorDependencies
  ): IorNel[ProcessCompilationError, ComponentExecutor] = {
    NodeValidationExceptionHandler.handleExceptions {
      doCreateComponentExecutor[ComponentExecutor](deps)
    }(deps.nodeId, deps.metaData).toIor
  }

  private def doCreateComponentExecutor[ComponentExecutor](deps: ComponentExecutorDependencies): ComponentExecutor = {
    import deps._
    implicit val lazyParameterCreationStrategy: LazyParameterCreationStrategy =
      deps.componentDefinition.component match {
        // Services are created within Interpreter so for every engine, lazy parameters can be evaluable. Other component types
        // (Sources, Sinks and CustomComponent) have engine specific logic around lazy parameters.
        // For Flink, they need to be Serializable (PostponedEvaluatorLazyParameterStrategy)
        case _: Service => LazyParameterCreationStrategy.default
        case _          => deps.nonServicesLazyParamStrategy
      }
    val paramsMap = Params.fromParameterEvaluationResultMap(
      deps.compiledParameters.map { case (tp, p) =>
        // Reuse eager results precomputed during node parameters compilation. Entries for lazy params are skipped -
        // they are validation-only snapshots (context-free != deterministic, e.g. `#DATE.now()`) is not LazyParameter
        val precomputedResult = if (p.isLazyParameter) None else deps.compilationEvaluationResults.get(p.name)
        p.name -> precomputedResult.getOrElse(parameterEvaluator.evaluateParameter(tp, p))
      }.toMap
    )

    deps.componentDefinition.implementationInvoker
      .invokeMethod(
        paramsMap,
        deps.nodeCompilationDependencies,
        deps.invocationContext
      )
      .asInstanceOf[ComponentExecutor]
  }

}

object ComponentExecutorFactory {

  final class ComponentExecutorDependencies(
      val componentDefinition: ComponentDefinitionWithImplementation,
      val nodeCompilationDependencies: NodeCompilationDependencies,
      val compiledParameters: List[(TypedParameter, Parameter)],
      val compilationEvaluationResults: Map[ParameterName, EagerParameterEvaluationResult],
      val nonServicesLazyParamStrategy: LazyParameterCreationStrategy,
      val invocationContext: Option[ComponentImplementationSpecificInvocationContext],
  ) {
    implicit def nodeId: NodeId     = nodeCompilationDependencies.nodeId
    implicit def metaData: MetaData = nodeCompilationDependencies.metaData
    implicit def jobData: JobData   = nodeCompilationDependencies.jobData
  }

}
