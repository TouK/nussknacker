package pl.touk.nussknacker.engine.expression

import java.util.Optional

import cats.effect.IO
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.expression.Expression
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener, ValueWithContext}
import pl.touk.nussknacker.engine.compile.Validations
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ServiceInvoker
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.{ExecutionContext, Future}

/* We have 3 different places where expressions can be evaluated:
  - Interpreter - evaluation of service parameters and variable definitions
  - CompilerLazyInterpreter - evaluation of parameters of CustomStreamTransformers
  - ProcessObjectFactory - evaluation of exceptionHandler, source and sink parameters
  They are evaluated with different contexts - e.g. in interpreter we can use process variables, but in source/sink we can use only global ones.
*/
object ExpressionEvaluator {

  def optimizedEvaluator(globalVariablesPreparer: GlobalVariablesPreparer,
                         listeners: Seq[ProcessListener],
                         metaData: MetaData,
                         services: Map[String, ObjectWithMethodDef]): ExpressionEvaluator = {
    val lazyValuesProvider: (ExecutionContext, MetaData, String) => LazyValuesProvider = if (metaData.typeSpecificData.allowLazyVars) {
      realLazyValuesProvider(services)
    } else {
      (_, _, _) => ThrowingLazyValuesProvider
    }
    new ExpressionEvaluator(globalVariablesPreparer, listeners, Some(metaData), lazyValuesProvider)
  }

  //This is for evaluation expressions fixed expressions during object creation *and* during tests/service queries
  //Should *NOT* be used for evaluating expressions on events in *production*
  def unOptimizedEvaluator(globalVariablesPreparer: GlobalVariablesPreparer) =
    new ExpressionEvaluator(globalVariablesPreparer, Nil, None, (_, _, _) => ThrowingLazyValuesProvider)

  def unOptimizedEvaluator(modelData: ModelData): ExpressionEvaluator =
    unOptimizedEvaluator(GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig))


  private object ThrowingLazyValuesProvider extends LazyValuesProvider {
    override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): IO[(LazyContext, T)] =
      IO.raiseError(new IllegalArgumentException("Lazy values are currently not allowed when async interpretation is used."))
  }

  private def realLazyValuesProvider(services: Map[String, ObjectWithMethodDef])
                                      (ec: ExecutionContext, metaData: MetaData, nodeId: String) = new LazyValuesProvider {

    private implicit val iec: ExecutionContext = ec

    override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): IO[(LazyContext, T)] = {
      val paramsMap = params.toMap
      context.get[T](serviceId, paramsMap) match {
        case Some(value) =>
          IO.pure((context, value))
        case None =>
          //TODO: maybe it should be Later here???
          IO.fromFuture(IO.pure(evaluateValue[T](context.id, serviceId, paramsMap).map { value =>
            //TODO: exception?
            (context.withEvaluatedValue(serviceId, paramsMap, Left(value)), value)
          }))
      }
    }

    private def evaluateValue[T](ctxId: String, serviceId: String, paramsMap: Map[String, Any]): Future[T] = {
      services.get(serviceId) match {
        case None => Future.failed(new IllegalArgumentException(s"Service with id: $serviceId doesn't exist"))
        case Some(service) => ServiceInvoker(service).invoke(paramsMap, NodeContext(ctxId, nodeId, serviceId, None))(ec, metaData).map(_.asInstanceOf[T])
      }
    }
  }

}

class ExpressionEvaluator(globalVariablesPreparer: GlobalVariablesPreparer,
                          listeners: Seq[ProcessListener],
                          metaDataToUse: Option[MetaData],
                          lazyValuesProviderCreator: (ExecutionContext, MetaData, String) => LazyValuesProvider) {
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
    val valueWithModifiedContext = evaluate[AnyRef](param.expression, param.name, nodeId.id, ctx)
    valueWithModifiedContext.map { evaluatedValue =>
      if (param.shouldBeWrappedWithScalaOption)
        Option(evaluatedValue)
      else if (param.shouldBeWrappedWithJavaOptional)
        Optional.ofNullable(evaluatedValue)
      else
        evaluatedValue
    }
  }

  def evaluate[R](expr: Expression, expressionId: String, nodeId: String, ctx: Context)
                 (implicit metaData: MetaData): ValueWithContext[R] = {
    val lazyValuesProvider = lazyValuesProviderCreator(ecToUse, metaData, nodeId)
    val globalVariables = optimizedGlobals.getOrElse(prepareGlobals(metaData))

    val valueWithLazyContext = expr.evaluate[R](ctx, globalVariables, lazyValuesProvider)
    listeners.foreach(_.expressionEvaluated(nodeId, expressionId, expr.original, ctx, metaData, valueWithLazyContext.value))
    ValueWithContext(valueWithLazyContext.value, ctx.withLazyContext(valueWithLazyContext.lazyContext))
  }


}
