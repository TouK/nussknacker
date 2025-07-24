package pl.touk.nussknacker.engine.compile

import cats.data.IorNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, Params, Service}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.transformation.{OutputVariableNameValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.compile.ComponentExecutorFactory.ComponentExecutorDependencies
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, ParameterEvaluator}
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation

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
      componentDefinition.component match {
        // Services are created within Interpreter so for every engine, lazy parameters can be evaluable. Other component types
        // (Sources, Sinks and CustomComponent) have engine specific logic around lazy parameters.
        // For Flink, they need to be Serializable (PostponedEvaluatorLazyParameterStrategy)
        case _: Service => LazyParameterCreationStrategy.default
        case _          => nonServicesLazyParamStrategy
      }
    val paramsMap = Params.fromParameterEvaluationResultMap(
      compiledParameters.map { case (tp, p) =>
        p.name -> parameterEvaluator.evaluateParameter(tp, p)
      }.toMap
    )
    // TODO: refactor implementationInvoker's to not use AnyRefs
    val nodeDependenciesRaw = scenarioCompilationDependencies.nodeDependencies.map {
      case TypedNodeDependencyValue(value) => value.asInstanceOf[AnyRef]
      case _: OutputVariableNameValue =>
        throw new IllegalStateException(
          "Output variable name node dependency was used in implementation invoker context but shouldn't be"
        )
    }
    componentDefinition.implementationInvoker
      .invokeMethod(
        paramsMap,
        outputVariableNameOpt,
        nodeDependenciesRaw ++ Seq(deps.nodeId, componentUseContext) ++ additionalDependencies
      )
      .asInstanceOf[ComponentExecutor]
  }

}

object ComponentExecutorFactory {

  final case class ComponentExecutorDependencies(
      componentDefinition: ComponentDefinitionWithImplementation,
      compiledParameters: List[(TypedParameter, Parameter)],
      outputVariableNameOpt: Option[String],
      additionalDependencies: Seq[AnyRef],
      componentUseContext: ComponentUseContext,
      nonServicesLazyParamStrategy: LazyParameterCreationStrategy
  )(
      implicit val nodeId: NodeId,
      val scenarioCompilationDependencies: ScenarioCompilationDependencies
  ) {
    implicit def metaData: MetaData = jobData.metaData
    implicit def jobData: JobData   = scenarioCompilationDependencies.jobData
  }

}
