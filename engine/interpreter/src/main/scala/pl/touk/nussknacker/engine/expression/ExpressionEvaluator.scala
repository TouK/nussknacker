package pl.touk.nussknacker.engine.expression

import cats.effect.IO
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.expression.Expression
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener, ValueWithContext}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ServiceInvoker
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.{ExecutionContext, Future}

/* We have 3 different places where expressions can be evaluated:
  - Interpreter - evaluation of service parameters and variable definitions
  - CompilerLazyInterpreter - evaluation of parameters of CustomStreamTransformers
  - ProcessObjectFactory - evaluation of exceptionHandler, source and sink parameters
  They are evaluated with different contexts - e.g. in interpreter we can use process variables, but in source/sink we can use only global ones.
*/
object ExpressionEvaluator {

  def withLazyVals(globalVariablesPreparer: GlobalVariablesPreparer, listeners: Seq[ProcessListener], services: Map[String, ObjectWithMethodDef]) =
      new ExpressionEvaluator(globalVariablesPreparer, listeners, realLazyValuesProvider(services))

  def withoutLazyVals(globalVariablesPreparer: GlobalVariablesPreparer, listeners: Seq[ProcessListener]) =
    new ExpressionEvaluator(globalVariablesPreparer, listeners, (_, _, _) => ThrowingLazyValuesProvider)


  private object ThrowingLazyValuesProvider extends LazyValuesProvider {
    override def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): IO[(LazyContext, T)] =
      IO.raiseError(new IllegalArgumentException("Lazy values are currently not allowed when async interpretation is used."))
  }

  private def realLazyValuesProvider(services: Map[String, ObjectWithMethodDef])
                                      (ec: ExecutionContext, metaData: MetaData, nodeId: String) = new LazyValuesProvider {

    private implicit val iec = ec

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
                          listeners: Seq[ProcessListener], lazyValuesProviderCreator: (ExecutionContext, MetaData, String) => LazyValuesProvider) {


  def evaluateParameters(params: List[pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter], ctx: Context)
                                  (implicit nodeId: NodeId, metaData: MetaData, ec: ExecutionContext) : Future[(Context, Map[String, AnyRef])] = {
    params.foldLeft(Future.successful((ctx, Map.empty[String, AnyRef]))) {
      case (fut, param) => fut.flatMap { case (accCtx, accParams) =>
        evaluate[AnyRef](param.expression, param.name, nodeId.id, accCtx).map { valueWithModifiedContext =>
          val newAccParams = accParams + (param.name -> valueWithModifiedContext.value)
          (valueWithModifiedContext.context, newAccParams)
        }
      }
    }
  }

  def evaluate[R](expr: Expression, expressionId: String, nodeId: String, ctx: Context)
                 (implicit ec: ExecutionContext, metaData: MetaData): Future[ValueWithContext[R]] = {
    val lazyValuesProvider = lazyValuesProviderCreator(ec, metaData, nodeId)
    val ctxWithGlobals = ctx.withVariables(globalVariablesPreparer.prepareGlobalVariables(metaData).mapValues(_.obj))

    expr.evaluate[R](ctxWithGlobals, lazyValuesProvider).map { valueWithLazyContext =>
      listeners.foreach(_.expressionEvaluated(nodeId, expressionId, expr.original, ctx, metaData, valueWithLazyContext.value))
      ValueWithContext(valueWithLazyContext.value, ctx.withLazyContext(valueWithLazyContext.lazyContext))
    }
  }


}
