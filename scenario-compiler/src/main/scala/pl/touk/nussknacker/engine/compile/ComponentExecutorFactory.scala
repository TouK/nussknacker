package pl.touk.nussknacker.engine.compile

import cats.data.IorNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.compile.ComponentExecutorFactory.ComponentExecutorDependencies
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, ParameterEvaluator}
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext

import scala.reflect.{classTag, ClassTag}

// This class helps to create an Executor using Component. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
class ComponentExecutorFactory(parameterEvaluator: ParameterEvaluator) extends LazyLogging {

  def createComponentExecutor[ComponentExecutor: ClassTag](
      deps: ComponentExecutorDependencies
  ): IorNel[ProcessCompilationError, ComponentExecutor] = {
    NodeValidationExceptionHandler.handleExceptions {
      doCreateComponentExecutor[ComponentExecutor](deps)
    }(deps.nodeId, deps.nodeName, deps.metaData).toIor
  }

  private def doCreateComponentExecutor[ComponentExecutor: ClassTag](
      deps: ComponentExecutorDependencies
  ): ComponentExecutor = {
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
        p.name -> parameterEvaluator.evaluateParameter(tp, p).toTry.get
      }.toMap
    )

    val invocationResult = deps.componentDefinition.implementationInvoker
      .invokeMethod(
        paramsMap,
        deps.nodeCompilationDependencies,
        deps.invocationContext
      )
    invocationResult match {
      case a: ComponentExecutor => a
      case _ =>
        throw new ClassCastException(
          s"Object returned by [${deps.componentDefinition.id}] component is ${Option(invocationResult)
              .map(ir => s"of ${ir.getClass.getName} class")
              .orNull} but should be a ${classTag[ComponentExecutor].runtimeClass.getName}"
        )
    }
  }

}

object ComponentExecutorFactory {

  final class ComponentExecutorDependencies(
      val componentDefinition: ComponentDefinitionWithImplementation,
      val nodeCompilationDependencies: NodeCompilationDependencies,
      val compiledParameters: List[(TypedParameter, Parameter)],
      val nonServicesLazyParamStrategy: LazyParameterCreationStrategy,
      val invocationContext: Option[ComponentImplementationSpecificInvocationContext],
  ) {
    implicit def nodeId: NodeId     = nodeCompilationDependencies.nodeId
    implicit def nodeName: NodeName = nodeCompilationDependencies.nodeName
    implicit def metaData: MetaData = nodeCompilationDependencies.metaData
    implicit def jobData: JobData   = nodeCompilationDependencies.jobData
  }

}
