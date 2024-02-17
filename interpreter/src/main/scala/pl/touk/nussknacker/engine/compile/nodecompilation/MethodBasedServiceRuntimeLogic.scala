package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentWithDefinition

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceRuntimeLogic(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefinition: ComponentWithDefinition,
    parametersProvider: Context => Params
) extends ServiceRuntimeLogic
    with LazyLogging {

  override def apply(context: Context)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    // TODO: This might looks strange. It is because Non-eager services are the only components that
    //       are not factories - their @MethodToInvoke is the runtime logic. We should remove the support
    //       for this kind of Components, and enforce usage of EagerService in this place.
    componentDefinition.runtimeLogicFactory
      .createRuntimeLogic(
        parametersProvider(context),
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(ec, collector, metaData, nodeId, context, ContextId(context.id), componentUseCase)
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
