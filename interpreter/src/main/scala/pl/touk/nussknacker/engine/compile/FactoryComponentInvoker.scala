package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.compile.nodecompilation.ParameterEvaluator
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

class FactoryComponentInvoker(expressionEvaluator: ExpressionEvaluator) extends LazyLogging {

  private val parameterEvaluator = new ParameterEvaluator(expressionEvaluator)

  def invokeCreateMethod[T](
      nodeDefinition: ComponentDefinitionWithImplementation,
      compiledParameters: List[(TypedParameter, Parameter)],
      outputVariableNameOpt: Option[String],
      additionalDependencies: Seq[AnyRef],
      componentUseCase: ComponentUseCase
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    NodeValidationExceptionHandler.handleExceptions {
      doInvokeCreateMethod[T](
        nodeDefinition,
        compiledParameters,
        outputVariableNameOpt,
        additionalDependencies,
        componentUseCase
      )
    }
  }

  private def doInvokeCreateMethod[T](
      componentDefWithImpl: ComponentDefinitionWithImplementation,
      params: List[(evaluatedparam.TypedParameter, Parameter)],
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
