package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableWithFixedValue, Parameter => ParameterDef}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.nodecompilation.LazyParameterCreationStrategy.{
  EvaluableLazyParameterStrategy,
  PostponedEvaluatorLazyParameterStrategy
}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.{TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class ParameterEvaluator(
    globalVariablesPreparer: GlobalVariablesPreparer,
    listeners: Seq[ProcessListener]
) {

  private val compileTimeExpressionEvaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)
  private val runtimeExpressionEvaluator = ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners)

  private val contextToUse: Context = Context("objectCreate")

  def prepareParameter(
      typedParameter: TypedParameter,
      definition: ParameterDef
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): (AnyRef, BaseDefinedParameter) = {
    if (definition.isLazyParameter) {
      prepareLazyParameter(typedParameter, definition)
    } else {
      evaluateParam(typedParameter, definition)
    }
  }

  def evaluate(
      parameters: Iterable[CompiledParameter],
      context: Context
  )(implicit nodeId: NodeId, jobData: JobData): Map[ParameterName, AnyRef] = {
    parameters
      .map(p => p.name -> runtimeExpressionEvaluator.evaluateParameter(p, context).value)
      .toMap
  }

  private def prepareLazyParameter[T](param: TypedParameter, definition: ParameterDef)(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): (AnyRef, BaseDefinedParameter) = {
    param.typedValue match {
      case e: TypedExpression if !definition.branchParam =>
        (prepareLazyParameterExpression(definition, e), DefinedLazyParameter(e.returnType))
      case TypedExpressionMap(valueByKey) if definition.branchParam =>
        (
          valueByKey.mapValuesNow(prepareLazyParameterExpression(definition, _)),
          DefinedLazyBranchParameter(valueByKey.mapValuesNow(_.returnType))
        )
      case _ => throw new IllegalStateException()
    }
  }

  private def evaluateParam[T](
      param: TypedParameter,
      definition: ParameterDef
  )(implicit jobData: JobData, nodeId: NodeId): (AnyRef, BaseDefinedParameter) = {

    val additionalDefinitions = definition.additionalVariables.collect {
      case (name, AdditionalVariableWithFixedValue(value, _)) =>
        name -> value
    }
    val augumentedCtx = contextToUse.withVariables(additionalDefinitions)

    param.typedValue match {
      case e: TypedExpression if !definition.branchParam =>
        val evaluated = evaluateSync(CompiledParameter(e, definition), augumentedCtx)
        (evaluated, DefinedEagerParameter(evaluated, e.returnType))
      case TypedExpressionMap(valueByKey) if definition.branchParam =>
        val evaluated = valueByKey.mapValuesNow(exp => evaluateSync(CompiledParameter(exp, definition), augumentedCtx))
        (evaluated, DefinedEagerBranchParameter(evaluated, valueByKey.mapValuesNow(_.returnType)))
      case _ => throw new IllegalStateException()
    }
  }

  private def prepareLazyParameterExpression[T](definition: ParameterDef, exprValue: TypedExpression)(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): LazyParameter[Nothing] = {
    lazyParameterCreationStrategy match {
      case EvaluableLazyParameterStrategy =>
        val compiledParameter = CompiledParameter(exprValue, definition)
        EvaluableLazyParameterFactory.build(
          compiledParameter = compiledParameter,
          expressionEvaluator = runtimeExpressionEvaluator,
          nodeId = nodeId,
          jobData = jobData,
          typingResult = compiledParameter.typingInfo.typingResult
        )
      case PostponedEvaluatorLazyParameterStrategy =>
        new EvaluableLazyParameterCreator(
          nodeId,
          definition,
          graph.expression.Expression(exprValue.expression.language, exprValue.expression.original),
          exprValue.returnType
        )
    }
  }

  private def evaluateSync(
      param: CompiledParameter,
      ctx: Context
  )(implicit jobData: JobData, nodeId: NodeId): AnyRef = {
    compileTimeExpressionEvaluator.evaluateParameter(param, ctx).value
  }

}

sealed trait LazyParameterCreationStrategy

object LazyParameterCreationStrategy {

  val default: LazyParameterCreationStrategy   = EvaluableLazyParameterStrategy
  val postponed: LazyParameterCreationStrategy = PostponedEvaluatorLazyParameterStrategy

  private[nodecompilation] case object EvaluableLazyParameterStrategy extends LazyParameterCreationStrategy

  private[nodecompilation] case object PostponedEvaluatorLazyParameterStrategy extends LazyParameterCreationStrategy

}
