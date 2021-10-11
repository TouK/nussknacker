package pl.touk.nussknacker.engine.compiledgraph

import cats.implicits._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, ToCollect}
import pl.touk.nussknacker.engine.api.{Context, ContextId, MetaData, ServiceInvoker}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.resultcollector.{CollectableAction, ResultCollector, TransmissionNames}

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(id: String,
                        invoker: ServiceInvoker,
                        parameters: List[Parameter],
                        resultCollector: ResultCollector) {

    def invoke(ctx: Context, expressionEvaluator: ExpressionEvaluator)
              (implicit nodeId: NodeId, metaData: MetaData, ec: ExecutionContext, runMode: RunMode): (Map[String, AnyRef], Future[Any]) = {

      val (_, preparedParams) = expressionEvaluator.evaluateParameters(parameters, ctx)
      val contextId = ContextId(ctx.id)
      val collector = new BaseServiceInvocationCollector(resultCollector, contextId, nodeId)
      (preparedParams, invoker.invokeService(preparedParams)(ec, collector, contextId, runMode))
    }
  }

  private[service] class BaseServiceInvocationCollector(resultCollector: ResultCollector,
                                       contextId: ContextId,
                                       nodeId: NodeId) extends ServiceInvocationCollector {

    def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(action: => Future[CollectableAction[A]], names: TransmissionNames = TransmissionNames.default)
                              (implicit ec: ExecutionContext): Future[A] = {
      resultCollector.collectWithResponse(contextId, nodeId, request, mockValue, action, names)
    }
  }


}
