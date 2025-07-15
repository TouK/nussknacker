package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableWithFixedValue, Parameter => ParameterDef}
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

  private val contextToUse: Context = Context.dummy

  def evaluateParameterToRawValue(
      context: Context,
      p: CompiledParameter
  )(implicit nodeId: NodeId, jobData: JobData): AnyRef = {
    runtimeExpressionEvaluator.evaluateParameter(p, context).value
  }

  def evaluateParameter(
      typedParameter: TypedParameter,
      definition: ParameterDef,
      nodeInputValidationContext: NodeInputValidationContext,
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): ParameterEvaluationResult = {
    if (definition.isLazyParameter) {
      evaluateLazyParameter(typedParameter, definition, nodeInputValidationContext)
    } else {
      prepareEagerParameter(typedParameter, definition)
    }
  }

  private def evaluateLazyParameter(
      param: TypedParameter,
      definition: ParameterDef,
      nodeInputValidationContext: NodeInputValidationContext,
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): LazyParameterEvaluationResult = {
    (param.typedValue, nodeInputValidationContext) match {
      case (e: TypedExpression, SingleInputNodeInputValidationContext(singleCtx)) if !definition.branchParam =>
        SingleLazyParameterEvaluationResult(prepareLazyParameterExpression(definition, e, singleCtx))
      case (
            TypedExpressionMap(valueByKey),
            MultipleInputBranchesNodeInputValidationContext(validationContextByBranchId)
          ) if definition.branchParam =>
        BranchLazyParameterEvaluationResult(valueByKey.map { case (branchId, typedExpression) =>
          val branchValidationContext = validationContextByBranchId.getOrElse(
            branchId,
            throw new IllegalStateException(
              s"Missing validation context for branch [$branchId] during lazy parameter compilation. Only validation contexts for branches [${validationContextByBranchId.keys.mkString(", ")}] are available"
            )
          )
          branchId -> prepareLazyParameterExpression(definition, typedExpression, branchValidationContext)
        })
      case _ =>
        throw new IllegalStateException(
          s"Illegal combination of typed parameter [$param] and node input validation context [$nodeInputValidationContext]"
        )
    }
  }

  private def prepareEagerParameter(
      param: TypedParameter,
      definition: ParameterDef
  )(implicit jobData: JobData, nodeId: NodeId): EagerParameterEvaluationResult = {
    val additionalDefinitions = definition.additionalVariables.collect {
      case (name, AdditionalVariableWithFixedValue(value, _)) =>
        name -> value
    }
    val augumentedCtx = contextToUse.withVariables(additionalDefinitions)

    param.typedValue match {
      case e: TypedExpression if !definition.branchParam =>
        val evaluatedValue = evaluateSync(CompiledParameter(e, definition), augumentedCtx)
        SingleEagerParameterEvaluationResult(evaluatedValue, e.returnType)
      case TypedExpressionMap(valueByKey) if definition.branchParam =>
        val evaluatedValuesByBranchId =
          valueByKey.mapValuesNow(exp => evaluateSync(CompiledParameter(exp, definition), augumentedCtx))
        BranchEagerParameterEvaluationResult(evaluatedValuesByBranchId, valueByKey.mapValuesNow(_.returnType))
      case _ => throw new IllegalStateException()
    }
  }

  private def prepareLazyParameterExpression(
      definition: ParameterDef,
      exprValue: TypedExpression,
      validationContext: ValidationContext
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): LazyParameter[Nothing] = {
    lazyParameterCreationStrategy match {
      case EvaluableLazyParameterStrategy =>
        new EvaluableLazyParameter(
          CompiledParameter(exprValue, definition),
          runtimeExpressionEvaluator,
          nodeId,
          jobData
        )
      case PostponedEvaluatorLazyParameterStrategy =>
        new EvaluableLazyParameterCreator(
          nodeId,
          definition,
          graph.expression.Expression(exprValue.expression.language, exprValue.expression.original),
          validationContext,
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
