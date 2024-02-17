package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithLogic

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceLogic(
                                                        metaData: MetaData,
                                                        nodeId: NodeId,
                                                        outputVariableNameOpt: Option[OutputVar],
                                                        componentDefWithImpl: ComponentDefinitionWithLogic,
                                                        parametersProvider: Context => Map[String, Any]
) extends ServiceLogic
    with LazyLogging {

  override def run(context: Context)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    componentDefWithImpl.componentLogic
      .run(
        parametersProvider(context),
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(ec, collector, metaData, nodeId, context, ContextId(context.id), componentUseCase)
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
