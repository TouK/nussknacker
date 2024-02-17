package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentWithRuntimeLogicFactory

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceRuntimeLogic(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefWithImpl: ComponentWithRuntimeLogicFactory
) extends ServiceRuntimeLogic
    with LazyLogging {

  override def apply(params: Map[String, Any])(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ContextId,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    componentDefWithImpl.runtimeLogicFactory
      .createRuntimeLogic(
        params,
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(ec, collector, metaData, nodeId, contextId, componentUseCase)
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
