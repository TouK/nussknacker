package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{
  CollectableAction,
  ServiceInvocationCollector,
  ToCollect,
  TransmissionNames
}
import pl.touk.nussknacker.engine.api.{Context, ContextId, MetaData, ServiceRuntimeLogic}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import cats.implicits._
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ServiceExecutionContext}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(
      id: String,
      invoker: ServiceRuntimeLogic,
      parameters: List[CompiledParameter],
      resultCollector: ResultCollector,
  ) {

    def invoke(
        ctx: Context,
        expressionEvaluator: ExpressionEvaluator,
        serviceExecutionContext: ServiceExecutionContext
    )(
        implicit nodeId: NodeId,
        metaData: MetaData,
        componentUseCase: ComponentUseCase
    ): (Map[String, AnyRef], Future[Any]) = {

      val (_, preparedParams) = expressionEvaluator.evaluateParameters(parameters, ctx)
      val contextId           = ContextId(ctx.id)
      val collector           = new BaseServiceInvocationCollector(resultCollector, contextId, nodeId, id)
      (
        preparedParams,
        invoker.apply(preparedParams)(
          serviceExecutionContext.executionContext,
          collector,
          contextId,
          componentUseCase
        )
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
