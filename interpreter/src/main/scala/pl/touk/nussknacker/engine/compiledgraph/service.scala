package pl.touk.nussknacker.engine.compiledgraph

import cats.implicits._
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ServiceExecutionContext}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{
  CollectableAction,
  ServiceInvocationCollector,
  ToCollect,
  TransmissionNames
}
import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId, ServiceRuntimeLogic}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(
      id: String,
      runtimeLogic: ServiceRuntimeLogic,
      parameters: List[CompiledParameter],
      resultCollector: ResultCollector,
  ) {

    def invoke(ctx: Context, serviceExecutionContext: ServiceExecutionContext)(
        implicit nodeId: NodeId,
        componentUseCase: ComponentUseCase
    ): Future[Any] = {

      val contextId = ContextId(ctx.id)
      val collector = new BaseServiceInvocationCollector(resultCollector, contextId, nodeId, id)
      runtimeLogic.apply(ctx)(
        serviceExecutionContext.executionContext,
        collector,
        componentUseCase
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
