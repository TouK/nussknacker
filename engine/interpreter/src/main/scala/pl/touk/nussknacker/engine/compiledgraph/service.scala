package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollectorForContext
import pl.touk.nussknacker.engine.api.{Context, ContextId, MetaData, ServiceInvoker}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

import scala.concurrent.{ExecutionContext, Future}

object service {

  case class ServiceRef(id: String,
                        invoker: ServiceInvoker,
                        parameters: List[Parameter],
                        invocationsCollector: ServiceInvocationCollectorForContext) {

    def invoke(ctx: Context, expressionEvaluator: ExpressionEvaluator)
              (implicit nodeId: NodeId, metaData: MetaData, ec: ExecutionContext): (Map[String, AnyRef], Future[AnyRef]) = {

      val (_, preparedParams) = expressionEvaluator.evaluateParameters(parameters, ctx)
      val contextId = ContextId(ctx.id)
      (preparedParams, invoker.invokeService(preparedParams)(ec, invocationsCollector(contextId), contextId))
    }
  }

}
