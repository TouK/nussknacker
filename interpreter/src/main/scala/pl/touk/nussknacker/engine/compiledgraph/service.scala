package pl.touk.nussknacker.engine.compiledgraph

import cats.implicits._
import pl.touk.nussknacker.engine.api.ServiceLogic.{ParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ServiceExecutionContext}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{
  CollectableAction,
  ServiceInvocationCollector,
  ToCollect,
  TransmissionNames
}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(
      id: String,
      serviceLogic: ServiceLogic,
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
    ): Future[Any] = {
      val evaluator = ParamsEvaluator.create(ctx, expressionEvaluator.evaluateParameters(parameters, _)._2)
      val contextId = ContextId(ctx.id)
      val runContext = RunContext(
        collector = new BaseServiceInvocationCollector(resultCollector, contextId, nodeId, id),
        contextId = contextId,
        componentUseCase = componentUseCase
      )
      serviceLogic.run(evaluator)(runContext, serviceExecutionContext.executionContext)
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
