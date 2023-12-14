package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.compile.nodecompilation.ParameterEvaluator
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

// This class helps to create an Executor using Component. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
class ComponentExecutorFactory(expressionEvaluator: ExpressionEvaluator) extends LazyLogging {

  private val parameterEvaluator = new ParameterEvaluator(expressionEvaluator)

  def createComponentExecutor[ComponentExecutor](
      componentDefWithImpl: ComponentDefinitionWithImplementation,
      compiledParameters: List[(TypedParameter, Parameter)],
      outputVariableNameOpt: Option[String],
      additionalDependencies: Seq[AnyRef],
      componentUseCase: ComponentUseCase
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ComponentExecutor] = {
    NodeValidationExceptionHandler.handleExceptions {
      doCreateComponentExecutor[ComponentExecutor](
        componentDefWithImpl,
        compiledParameters,
        outputVariableNameOpt,
        additionalDependencies,
        componentUseCase
      )
    }
  }

  private def doCreateComponentExecutor[T](
      componentDefWithImpl: ComponentDefinitionWithImplementation,
      params: List[(TypedParameter, Parameter)],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef],
      componentUseCase: ComponentUseCase
  )(implicit processMetaData: MetaData, nodeId: NodeId): T = {
    val paramsMap = params.map { case (tp, p) =>
      p.name -> parameterEvaluator.prepareParameter(tp, p)._1
    }.toMap
    componentDefWithImpl.implementationInvoker
      .invokeMethod(paramsMap, outputVariableNameOpt, Seq(processMetaData, nodeId, componentUseCase) ++ additional)
      .asInstanceOf[T]
  }

}
