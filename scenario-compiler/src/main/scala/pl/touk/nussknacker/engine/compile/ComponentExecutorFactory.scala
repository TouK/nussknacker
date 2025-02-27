package pl.touk.nussknacker.engine.compile

import cats.data.IorNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, Params, Service}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, ParameterEvaluator}
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation

// This class helps to create an Executor using Component. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
class ComponentExecutorFactory(parameterEvaluator: ParameterEvaluator) extends LazyLogging {

  def createComponentExecutor[ComponentExecutor](
      component: ComponentDefinitionWithImplementation,
      compiledParameters: List[(TypedParameter, Parameter)],
      outputVariableNameOpt: Option[String],
      additionalDependencies: Seq[AnyRef],
      componentUseCase: ComponentUseCase,
      nonServicesLazyParamStrategy: LazyParameterCreationStrategy
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): IorNel[ProcessCompilationError, ComponentExecutor] = {
    NodeValidationExceptionHandler.handleExceptions {
      doCreateComponentExecutor[ComponentExecutor](
        component,
        compiledParameters,
        outputVariableNameOpt,
        additionalDependencies,
        componentUseCase,
        nonServicesLazyParamStrategy
      )
    }(nodeId, jobData.metaData).toIor
  }

  private def doCreateComponentExecutor[ComponentExecutor](
      componentDefinition: ComponentDefinitionWithImplementation,
      params: List[(TypedParameter, Parameter)],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef],
      componentUseCase: ComponentUseCase,
      nonServicesLazyParamStrategy: LazyParameterCreationStrategy
  )(
      implicit jobData: JobData,
      nodeId: NodeId
  ): ComponentExecutor = {
    implicit val lazyParameterCreationStrategy: LazyParameterCreationStrategy =
      componentDefinition.component match {
        // Services are created within Interpreter so for every engine, lazy parameters can be evaluable. Other component types
        // (Sources, Sinks and CustomComponent) have engine specific logic around lazy parameters.
        // For Flink, they need to be Serializable (PostponedEvaluatorLazyParameterStrategy)
        case _: Service => LazyParameterCreationStrategy.default
        case _          => nonServicesLazyParamStrategy
      }
    val paramsMap = Params(
      params.map { case (tp, p) => p.name -> parameterEvaluator.prepareParameter(tp, p)._1 }.toMap
    )
    componentDefinition.implementationInvoker
      .invokeMethod(paramsMap, outputVariableNameOpt, Seq(jobData.metaData, nodeId, componentUseCase) ++ additional)
      .asInstanceOf[ComponentExecutor]
  }

}
