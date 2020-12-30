package pl.touk.nussknacker.engine.expression

import java.util.Optional

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.expression.Expression
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener, ValueWithContext}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/* We have 3 different places where expressions can be evaluated:
  - Interpreter - evaluation of service parameters and variable definitions
  - CompilerLazyInterpreter - evaluation of parameters of CustomStreamTransformers
  - ProcessObjectFactory - evaluation of exceptionHandler, source and sink parameters
  They are evaluated with different contexts - e.g. in interpreter we can use process variables, but in source/sink we can use only global ones.
*/
object ExpressionEvaluator {

  def optimizedEvaluator(globalVariablesPreparer: GlobalVariablesPreparer,
                         listeners: Seq[ProcessListener],
                         metaData: MetaData): ExpressionEvaluator = {
    new ExpressionEvaluator(globalVariablesPreparer, listeners, Some(metaData))
  }

  //This is for evaluation expressions fixed expressions during object creation *and* during tests/service queries
  //Should *NOT* be used for evaluating expressions on events in *production*
  def unOptimizedEvaluator(globalVariablesPreparer: GlobalVariablesPreparer) =
    new ExpressionEvaluator(globalVariablesPreparer, Nil, None)

  def unOptimizedEvaluator(modelData: ModelData): ExpressionEvaluator =
    unOptimizedEvaluator(GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig))

}

class ExpressionEvaluator(globalVariablesPreparer: GlobalVariablesPreparer,
                          listeners: Seq[ProcessListener],
                          metaDataToUse: Option[MetaData]) {
  private implicit val ecToUse: ExecutionContext = SynchronousExecutionContext.ctx

  private def prepareGlobals(metaData: MetaData): Map[String, Any] = globalVariablesPreparer.prepareGlobalVariables(metaData).mapValues(_.obj)

  private val optimizedGlobals = metaDataToUse.map(prepareGlobals)

  def evaluateParameters(params: List[pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter], ctx: Context)
                        (implicit nodeId: NodeId, metaData: MetaData) : (Context, Map[String, AnyRef]) = {
    val (newCtx, evaluatedParams) = params.foldLeft((ctx, List.empty[(String, AnyRef)])) {
      case ( (accCtx, accParams), param) =>
        val valueWithModifiedContext = evaluateParameter(param, accCtx)
        val newAccParams = (param.name -> valueWithModifiedContext.value) :: accParams
        (valueWithModifiedContext.context, newAccParams)
    }
    //hopefully peformance will be a bit improved with https://github.com/scala/scala/pull/7118
    (newCtx, evaluatedParams.toMap)
  }

  def evaluateParameter(param: pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter, ctx: Context)
                          (implicit nodeId: NodeId, metaData: MetaData): ValueWithContext[AnyRef] = {
    try {
      val valueWithModifiedContext = evaluate[AnyRef](param.expression, param.name, nodeId.id, ctx)
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

  def evaluate[R](expr: Expression, expressionId: String, nodeId: String, ctx: Context)
                 (implicit metaData: MetaData): ValueWithContext[R] = {
    val globalVariables = optimizedGlobals.getOrElse(prepareGlobals(metaData))

    val value = expr.evaluate[R](ctx, globalVariables)
    listeners.foreach(_.expressionEvaluated(nodeId, expressionId, expr.original, ctx, metaData, value))
    ValueWithContext(value, ctx)
  }


}
