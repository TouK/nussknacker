package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableWithFixedValue, Parameter => ParameterDef}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.LazyParameterCreationStrategy.{
  EvaluableLazyParameterStrategy,
  PostponedEvaluatorLazyParameterStrategy
}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.{MultipleBranchesTypedValue, SingleBranchTypedValue, TypedExpression}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

object ParameterEvaluator {

  def apply(
      globalVariablesPreparer: GlobalVariablesPreparer,
      listeners: Seq[ProcessListener],
      expressionCompiler: ExpressionCompiler
  ): ParameterEvaluator =
    new ParameterEvaluator(
      compileTimeExpressionEvaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer),
      runtimeExpressionEvaluator = ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners),
      expressionCompiler = expressionCompiler
    )

}

class ParameterEvaluator(
    compileTimeExpressionEvaluator: ExpressionEvaluator,
    runtimeExpressionEvaluator: ExpressionEvaluator,
    expressionCompiler: ExpressionCompiler,
) {

  private val contextToUse: Context = Context.dummy

  def evaluateParameterToRawValue(
      context: Context,
      p: CompiledParameter
  )(implicit nodeId: NodeId, jobData: JobData): AnyRef = {
    runtimeExpressionEvaluator.evaluateParameter(p, context).value
  }

  def evaluateParameter(
      typedParameter: TypedParameter,
      definition: ParameterDef
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): ParameterEvaluationResult = {
    if (definition.isLazyParameter) {
      evaluateLazyParameter(typedParameter, definition)
    } else {
      evaluateEagerParameter(typedParameter, definition)
    }
  }

  private def evaluateLazyParameter(
      param: TypedParameter,
      definition: ParameterDef
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): LazyParameterEvaluationResult = {
    param.typedValue match {
      case SingleBranchTypedValue(e, singleCtx) if !definition.branchParam =>
        SingleLazyParameterEvaluationResult(prepareLazyParameterExpression(definition, e, singleCtx))
      case MultipleBranchesTypedValue(valueByBranchId) if definition.branchParam =>
        BranchLazyParameterEvaluationResult(
          valueByBranchId.map { case (branchId, SingleBranchTypedValue(e, singleCtx)) =>
            branchId -> prepareLazyParameterExpression(definition, e, singleCtx)
          }
        )
      case _ =>
        throw new IllegalStateException(
          s"Illegal combination of typed parameter [$param] and typed value [${param.typedValue}]"
        )
    }
  }

  def evaluateEagerParameter(
      param: TypedParameter,
      definition: ParameterDef
  )(implicit jobData: JobData, nodeId: NodeId): EagerParameterEvaluationResult = {
    val additionalDefinitions = definition.additionalVariables.collect {
      case (name, AdditionalVariableWithFixedValue(value, _)) =>
        name -> value
    }
    val augmentedCtx = contextToUse.withVariables(additionalDefinitions)

    param.typedValue match {
      case single: SingleBranchTypedValue if !definition.branchParam =>
        val evaluatedValue = evaluateSync(CompiledParameter(single.typedExpression, definition), augmentedCtx)
        SingleEagerParameterEvaluationResult(evaluatedValue, single.typedExpression.returnType)
      case MultipleBranchesTypedValue(valueByBranchId) if definition.branchParam =>
        val evaluatedValuesByBranchId =
          valueByBranchId.mapValuesNow(exp =>
            evaluateSync(CompiledParameter(exp.typedExpression, definition), augmentedCtx)
          )
        BranchEagerParameterEvaluationResult(
          evaluatedValuesByBranchId,
          valueByBranchId.mapValuesNow(_.typedExpression.returnType)
        )
      case _ => throw new IllegalStateException()
    }
  }

  private def prepareLazyParameterExpression(
      definition: ParameterDef,
      typedExpression: TypedExpression,
      validationContext: ValidationContext
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): LazyParameter[Nothing] = {
    lazyParameterCreationStrategy match {
      case EvaluableLazyParameterStrategy =>
        val compiledValidators =
          expressionCompiler.compileValidatorsOrThrow(definition, validationContext.globalVariables)
        new EvaluableLazyParameter(
          CompiledParameter(typedExpression, definition, compiledValidators),
          runtimeExpressionEvaluator,
          nodeId,
          jobData
        )
      case PostponedEvaluatorLazyParameterStrategy =>
        new EvaluableLazyParameterCreator(
          nodeId,
          definition,
          typedExpression.expression.toExpression,
          validationContext,
          typedExpression.returnType
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
