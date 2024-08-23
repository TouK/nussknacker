package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compiledgraph.{BaseCompiledParameter, CompiledParameter}
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

/* We have 3 different places where expressions can be evaluated:
  - Interpreter - evaluation of service parameters and variable definitions
  - CompilerLazyInterpreter - evaluation of parameters of CustomStreamTransformers
  - ComponentExecutorFactory - evaluation of eager parameters for all components that are Executor's factories and for ExceptionHandler
  // todo ValidationExpressionParameterValidator
  They are evaluated with different contexts - e.g. in interpreter we can use process variables, but in source/sink we can use only global ones.
 */
object ExpressionEvaluator {

  def optimizedEvaluator(
      globalVariablesPreparer: GlobalVariablesPreparer,
      listeners: Seq[ProcessListener],
  ): ExpressionEvaluator = {
    new ExpressionEvaluator(globalVariablesPreparer, listeners, cacheGlobalVariables = true)
  }

  // This is for evaluation expressions fixed expressions during object creation *and* during tests/service queries
  // Should *NOT* be used for evaluating expressions on events in *production*
  def unOptimizedEvaluator(globalVariablesPreparer: GlobalVariablesPreparer) =
    new ExpressionEvaluator(globalVariablesPreparer, Nil, cacheGlobalVariables = false)

}

class ExpressionEvaluator(
    globalVariablesPreparer: GlobalVariablesPreparer,
    listeners: Seq[ProcessListener],
    cacheGlobalVariables: Boolean
) {
  private def prepareGlobals(metaData: MetaData): Map[String, Any] =
    globalVariablesPreparer.prepareGlobalVariables(metaData).mapValuesNow(_.obj)

  // We have an assumption, that ExpressionEvaluator will be used only with the same scenario
  private val optimizedGlobals: AtomicReference[Option[Map[String, Any]]] = new AtomicReference(None)

  def evaluateParameters(
      params: List[CompiledParameter],
      ctx: Context
  )(implicit nodeId: NodeId, metaData: MetaData): (Context, Map[ParameterName, AnyRef]) = {
    val (newCtx, evaluatedParams) = params.foldLeft((ctx, List.empty[(ParameterName, AnyRef)])) {
      case ((accCtx, accParams), param) =>
        val valueWithModifiedContext = evaluateParameter(param, accCtx)
        val newAccParams             = (param.name -> valueWithModifiedContext.value) :: accParams
        (valueWithModifiedContext.context, newAccParams)
    }
    // hopefully performance will be a bit improved with https://github.com/scala/scala/pull/7118
    (newCtx, evaluatedParams.toMap)
  }

  def evaluateParameter(
      param: BaseCompiledParameter,
      ctx: Context
  )(implicit nodeId: NodeId, metaData: MetaData): ValueWithContext[AnyRef] = {
    try {
      val valueWithModifiedContext = evaluate[AnyRef](param.expression, param.name.value, nodeId.id, ctx)
      valueWithModifiedContext.map { evaluatedValue =>
        if (param.shouldBeWrappedWithScalaOption)
          Option(evaluatedValue)
        else if (param.shouldBeWrappedWithJavaOptional)
          Optional.ofNullable(evaluatedValue)
        else
          evaluatedValue
      }
    } catch {
      case NonFatal(ex) => throw CustomNodeValidationException(ex.getMessage, Some(param.name), ex)
    }
  }

  def evaluate[R](expr: CompiledExpression, expressionId: String, nodeId: String, ctx: Context)(
      implicit metaData: MetaData
  ): ValueWithContext[R] = {
    val globalVariables = if (cacheGlobalVariables) {
      optimizedGlobals
        .updateAndGet { initializedVariablesOpt =>
          Some(initializedVariablesOpt.getOrElse(prepareGlobals(metaData)))
        }
        .getOrElse {
          throw new IllegalStateException("Optimized global variables not initialized")
        }
    } else {
      prepareGlobals(metaData)
    }

    val value = expr.evaluate[R](ctx, globalVariables)
    listeners.foreach(_.expressionEvaluated(nodeId, expressionId, expr.original, ctx, metaData, value))
    ValueWithContext(value, ctx)
  }

}
