package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableWithFixedValue, Parameter => ParameterDef}
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.compile.nodecompilation.LazyParameterCreationStrategy.{
  EvaluableLazyParameterStrategy,
  PostponedEvaluatorLazyParameterStrategy
}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.{MultipleBranchesTypedValue, SingleBranchTypedValue, TypedExpression}
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class ParameterEvaluator(
    globalVariablesPreparer: GlobalVariablesPreparer,
    listeners: Seq[ProcessListener],
    enableRuntimeParameterValidation: Boolean = false,
) {

  private val compileTimeExpressionEvaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)

  private val runtimeExpressionEvaluator = ExpressionEvaluator.optimizedEvaluator(
    globalVariablesPreparer,
    listeners,
    enableRuntimeParameterValidation,
  )

  private val contextToUse: Context = Context.dummy

  def evaluateParameterToRawValue(
      context: Context,
      p: CompiledParameter
  )(implicit nodeId: NodeId, jobData: JobData): AnyRef = {
    runtimeExpressionEvaluator.evaluateParameter(p, context).toTry.get.value
  }

  def evaluateParameter(
      typedParameter: TypedParameter,
      definition: ParameterDef
  )(
      implicit jobData: JobData,
      nodeId: NodeId,
      lazyParameterCreationStrategy: LazyParameterCreationStrategy
  ): Either[CustomNodeValidationException, ParameterEvaluationResult] = {
    if (definition.isLazyParameter) {
      Right(evaluateLazyParameter(typedParameter, definition))
    } else {
      prepareEagerParameter(typedParameter, definition)
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

  private def prepareEagerParameter(
      param: TypedParameter,
      definition: ParameterDef
  )(
      implicit jobData: JobData,
      nodeId: NodeId
  ): Either[CustomNodeValidationException, EagerParameterEvaluationResult] = {
    val additionalDefinitions = definition.additionalVariables.collect {
      case (name, AdditionalVariableWithFixedValue(value, _)) =>
        name -> value
    }
    val augumentedCtx = contextToUse.withVariables(additionalDefinitions)

    param.typedValue match {
      case single: SingleBranchTypedValue if !definition.branchParam =>
        evaluateSync(CompiledParameter(single.typedExpression, definition), augumentedCtx).map { evaluatedValue =>
          SingleEagerParameterEvaluationResult(evaluatedValue, single.typedExpression.returnType)
        }
      case MultipleBranchesTypedValue(valueByBranchId) if definition.branchParam =>
        valueByBranchId.toList
          .map { case (branchId, exp) =>
            evaluateSync(CompiledParameter(exp.typedExpression, definition), augumentedCtx).map(branchId -> _)
          }
          .sequence
          .map(_.toMap)
          .map { evaluatedValuesByBranchId =>
            BranchEagerParameterEvaluationResult(
              evaluatedValuesByBranchId,
              valueByBranchId.mapValuesNow(_.typedExpression.returnType)
            )
          }
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
    val creator = new EvaluableLazyParameterCreator[Nothing](
      nodeId,
      definition,
      graph.expression.Expression(exprValue.expression.language, exprValue.expression.original),
      validationContext,
      exprValue.returnType
    )
    lazyParameterCreationStrategy match {
      case EvaluableLazyParameterStrategy =>
        new EvaluableLazyParameter[Nothing](
          creator,
          CompiledParameter(exprValue, definition),
          runtimeExpressionEvaluator,
          nodeId,
          jobData
        )
      case PostponedEvaluatorLazyParameterStrategy =>
        creator
    }
  }

  private def evaluateSync(
      param: CompiledParameter,
      ctx: Context
  )(implicit jobData: JobData, nodeId: NodeId): Either[CustomNodeValidationException, AnyRef] = {
    compileTimeExpressionEvaluator.evaluateParameter(param, ctx).map(_.value)
  }

}

sealed trait LazyParameterCreationStrategy

object LazyParameterCreationStrategy {

  val default: LazyParameterCreationStrategy   = EvaluableLazyParameterStrategy
  val postponed: LazyParameterCreationStrategy = PostponedEvaluatorLazyParameterStrategy

  private[nodecompilation] case object EvaluableLazyParameterStrategy extends LazyParameterCreationStrategy

  private[nodecompilation] case object PostponedEvaluatorLazyParameterStrategy extends LazyParameterCreationStrategy

}
