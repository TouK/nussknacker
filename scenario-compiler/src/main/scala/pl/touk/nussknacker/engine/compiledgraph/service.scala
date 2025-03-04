package pl.touk.nussknacker.engine.compiledgraph

import cats.implicits._
import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId, ServiceInvoker}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.process.{ComponentUseContext, ServiceExecutionContext}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{
  CollectableAction,
  ServiceInvocationCollector,
  ToCollect,
  TransmissionNames
}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(
      id: String,
      invoker: ServiceInvoker,
      resultCollector: ResultCollector,
  ) {

    def invoke(
        ctx: Context,
        serviceExecutionContext: ServiceExecutionContext,
    )(
        implicit nodeId: NodeId,
        componentUseContext: ComponentUseContext,
    ): Future[Any] = {

      val contextId = ContextId(ctx.id)
      val collector = new BaseServiceInvocationCollector(resultCollector, contextId, nodeId, id)
      invoker.invoke(ctx)(
        serviceExecutionContext.executionContext,
        collector,
        componentUseContext,
      )
    }

  }

  private[service] class BaseServiceInvocationCollector(
      resultCollector: ResultCollector,
      contextId: ContextId,
      nodeId: NodeId,
      serviceRef: String
  ) extends ServiceInvocationCollector {

    def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(
        action: => Future[CollectableAction[A]],
        names: TransmissionNames = TransmissionNames.default
    )(implicit ec: ExecutionContext): Future[A] = {
      resultCollector.collectWithResponse(contextId, nodeId, serviceRef, request, mockValue, action, names)
    }

  }

}
