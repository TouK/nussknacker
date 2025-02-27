package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceInvoker(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefinition: ComponentDefinitionWithImplementation,
    parametersProvider: Context => Params
) extends ServiceInvoker
    with LazyLogging {

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseContext: ComponentUseContext,
  ): Future[AnyRef] = {
    componentDefinition.implementationInvoker
      .invokeMethod(
        parametersProvider(context),
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(ec, collector, metaData, nodeId, context, ContextId(context.id), componentUseContext)
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
