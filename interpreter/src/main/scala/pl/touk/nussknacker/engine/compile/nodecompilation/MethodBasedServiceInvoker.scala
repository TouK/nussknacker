package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceInvoker(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefWithImpl: ComponentDefinitionWithImplementation
) extends ServiceInvoker
    with LazyLogging {

  override def invokeService(context: Context, params: Map[String, Any])(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    componentDefWithImpl.implementationInvoker
      .invokeMethod(
        params,
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(ec, collector, metaData, nodeId, ContextId(context.id), componentUseCase)
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
