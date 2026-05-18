package pl.touk.nussknacker.engine.expression

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ParameterValidationError
import pl.touk.nussknacker.engine.api.exception.ParameterValidationAtRuntimeException
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.compiledgraph.{BaseCompiledParameter, CompiledParameter}
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

/* We have 4 different places where expressions can be evaluated:
  - Interpreter - evaluation of service parameters and variable definitions
  - CompilerLazyInterpreter - evaluation of parameters of CustomStreamTransformers
  - ComponentExecutorFactory - evaluation of eager parameters for all components that are Executor's factories and for ExceptionHandler
  - ValidationExpressionParameterValidator (inside ExpressionCompiler) - evaluation of validation expressions
  They are evaluated with different contexts - e.g. in interpreter we can use process variables, but in source/sink we can use only global ones.
 */
object ExpressionEvaluator {

  def optimizedEvaluator(
      globalVariablesPreparer: GlobalVariablesPreparer,
      listeners: Seq[ProcessListener],
      enableRuntimeParameterValidation: Boolean = false,
  ): ExpressionEvaluator = {
    new ExpressionEvaluator(
      globalVariablesPreparer,
      listeners,
      cacheGlobalVariables = true,
      enableRuntimeParameterValidation
    )
  }

  // This is for evaluation expressions fixed expressions during object creation *and* during tests/service queries
  // Should *NOT* be used for evaluating expressions on events in *production*
  def unOptimizedEvaluator(globalVariablesPreparer: GlobalVariablesPreparer) =
    new ExpressionEvaluator(globalVariablesPreparer, Nil, cacheGlobalVariables = false)

}

class ExpressionEvaluator(
    globalVariablesPreparer: GlobalVariablesPreparer,
    listeners: Seq[ProcessListener],
    cacheGlobalVariables: Boolean,
    enableRuntimeParameterValidation: Boolean = false,
) {
  private def prepareGlobals(jobData: JobData): Map[String, Any] =
    globalVariablesPreparer.prepareGlobalVariables(jobData).mapValuesNow(_.obj)

  // We have an assumption, that ExpressionEvaluator will be used only with the same scenario
  private val optimizedGlobals: AtomicReference[Option[Map[String, Any]]] = new AtomicReference(None)

  def evaluateParameters(
      params: List[CompiledParameter],
      ctx: Context
  )(implicit nodeId: NodeId, jobData: JobData): (Context, Map[ParameterName, AnyRef]) = {
    val (newCtx, evaluatedParams) = params.foldLeft((ctx, List.empty[(ParameterName, AnyRef)])) {
      case ((accCtx, accParams), param) =>
        val valueWithModifiedContext = evaluateParameter(param, accCtx).toTry.get
        val newAccParams             = (param.name -> valueWithModifiedContext.value) :: accParams
        (valueWithModifiedContext.context, newAccParams)
    }
    // hopefully performance will be a bit improved with https://github.com/scala/scala/pull/7118
    (newCtx, evaluatedParams.toMap)
  }

  def evaluateParameter(
      param: BaseCompiledParameter,
      ctx: Context
  )(implicit nodeId: NodeId, jobData: JobData): Either[CustomNodeValidationException, ValueWithContext[AnyRef]] = {
    (try {
      Right(evaluate[AnyRef](param.expression, param.name.value, nodeId, ctx))
    } catch {
      case NonFatal(ex) => Left(CustomNodeValidationException(ex.getMessage, Some(param.name), ex))
    }).map { vwc =>
      if (enableRuntimeParameterValidation) {
        param match {
          case cp: CompiledParameter if cp.validators.nonEmpty =>
            validateParameterAtRuntime(cp, vwc.value)
          case _ =>
        }
      }
      vwc.map { evaluatedValue =>
        if (param.shouldBeWrappedWithScalaOption)
          Option(evaluatedValue)
        else if (param.shouldBeWrappedWithJavaOptional)
          Optional.ofNullable(evaluatedValue)
        else
          evaluatedValue
      }
    }
  }

  private def validateParameterAtRuntime(param: CompiledParameter, rawValue: AnyRef)(
      implicit nodeId: NodeId
  ): Unit = {
    val expression = Expression(param.expression.language, param.expression.original)
    param.validators.foreach { validator =>
      validator.isValid(param.name, expression, Some(rawValue), None) match {
        case Invalid(error) =>
          val errorMessage = error match {
            case e: ParameterValidationError => e.message
            case e                           => e.toString
          }
          throw new ParameterValidationAtRuntimeException(
            input = s"${param.name.value}=${rawValue}",
            message = s"Parameter '${param.name.value}' failed runtime validation: $errorMessage",
          )
        case Valid(_) =>
      }
    }
  }

  def evaluate[R](expr: CompiledExpression, expressionId: String, nodeId: NodeId, ctx: Context)(
      implicit jobData: JobData
  ): ValueWithContext[R] = {
    val globalVariables = if (cacheGlobalVariables) {
      optimizedGlobals
        .updateAndGet { initializedVariablesOpt =>
          Some(initializedVariablesOpt.getOrElse(prepareGlobals(jobData)))
        }
        .getOrElse {
          throw new IllegalStateException("Optimized global variables not initialized")
        }
    } else {
      prepareGlobals(jobData)
    }

    val value = expr.evaluate[R](ctx, globalVariables)
    listeners.foreach(_.expressionEvaluated(nodeId, expressionId, expr.original, ctx, jobData.metaData, value))
    ValueWithContext(value, ctx)
  }

}
