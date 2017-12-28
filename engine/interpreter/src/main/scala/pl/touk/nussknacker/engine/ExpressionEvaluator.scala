package pl.touk.nussknacker.engine

import cats.Now
import cats.effect.IO
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener, ValueWithContext}
import pl.touk.nussknacker.engine.compiledgraph.expression.Expression
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ServiceInvoker

import scala.concurrent.{ExecutionContext, Future}

object ExpressionEvaluator {

  def withLazyVals(globalVariables: Map[String, Any], listeners: Seq[ProcessListener], services: Map[String, ObjectWithMethodDef]) =
      new ExpressionEvaluator(globalVariables, listeners, realLazyValuesProvider(services))

  def withoutLazyVals(globalVariables: Map[String, Any], listeners: Seq[ProcessListener]) =
    new ExpressionEvaluator(globalVariables, listeners, (_, _, _) => ThrowingLazyValuesProvider)


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
          IO.fromFuture(Now(evaluateValue[T](context.id, serviceId, paramsMap).map { value =>
            //TODO: exception?
            (context.withEvaluatedValue(serviceId, paramsMap, Left(value)), value)
          }))
      }
    }

    private def evaluateValue[T](ctxId: String, serviceId: String, paramsMap: Map[String, Any]): Future[T] = {
      services.get(serviceId) match {
        case None => Future.failed(new IllegalArgumentException(s"Service with id: $serviceId doesn't exist"))
        case Some(service) => ServiceInvoker(service).invoke(paramsMap, NodeContext(ctxId, nodeId, serviceId))(ec, metaData).map(_.asInstanceOf[T])
      }
    }
  }

}

class ExpressionEvaluator(globalVariables: Map[String, Any],
                          listeners: Seq[ProcessListener], lazyValuesProviderCreator: (ExecutionContext, MetaData, String) => LazyValuesProvider) {



  def evaluate[R](expr: Expression, expressionId: String, nodeId: String, ctx: Context)
                         (implicit ec: ExecutionContext, metaData: MetaData): Future[ValueWithContext[R]] = {
    val lazyValuesProvider = lazyValuesProviderCreator(ec, metaData, nodeId)
    val ctxWithGlobals = ctx.withVariables(globalVariables)
    expr.evaluate[R](ctxWithGlobals, lazyValuesProvider).map { valueWithLazyContext =>
      listeners.foreach(_.expressionEvaluated(nodeId, expressionId, expr.original, ctx, metaData, valueWithLazyContext.value))
      ValueWithContext(valueWithLazyContext.value, ctx.withLazyContext(valueWithLazyContext.lazyContext))
    }
  }


}
