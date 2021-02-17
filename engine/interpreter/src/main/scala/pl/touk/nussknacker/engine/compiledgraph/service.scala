package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ServiceInvocationCollector, ToCollect, TransmissionNames}
import pl.touk.nussknacker.engine.api.{Context, ContextId, MetaData, ServiceInvoker}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import cats.implicits._
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(id: String,
                        invoker: ServiceInvoker,
                        parameters: List[Parameter],
                        resultCollector: ResultCollector) {

    def invoke(ctx: Context, expressionEvaluator: ExpressionEvaluator)
              (implicit nodeId: NodeId, metaData: MetaData, ec: ExecutionContext): (Map[String, AnyRef], Future[Any]) = {

      val (_, preparedParams) = expressionEvaluator.evaluateParameters(parameters, ctx)
      val contextId = ContextId(ctx.id)
      val collector = new BaseServiceInvocationCollector(resultCollector, contextId, nodeId, id)
      (preparedParams, invoker.invokeService(preparedParams)(ec, collector, contextId))
    }
  }

  private[service] class BaseServiceInvocationCollector(resultCollector: ResultCollector,
                                       contextId: ContextId,
                                       nodeId: NodeId,
                                       serviceRef: String
                                      ) extends ServiceInvocationCollector {

    def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(action: => Future[CollectableAction[A]], names: TransmissionNames = TransmissionNames.default)
                              (implicit ec: ExecutionContext): Future[A] = {
      resultCollector.collectWithResponse(contextId, nodeId, serviceRef, request, mockValue, action, names)
    }
  }


}
