package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.LazyServiceInvocationContext

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceInvoker(
    componentDefinition: ComponentDefinitionWithImplementation,
    nodeCompilationContext: NodeCompilationDependencies,
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
        params = parametersProvider(context),
        compilationDependencies = nodeCompilationContext,
        invocationContext = Some(LazyServiceInvocationContext(ec, collector, context))
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
