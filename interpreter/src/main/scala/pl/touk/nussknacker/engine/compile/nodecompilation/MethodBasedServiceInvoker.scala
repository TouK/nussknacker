package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation

import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceInvoker(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefWithImpl: ComponentDefinitionWithImplementation
) extends ServiceInvoker
    with LazyLogging {

  override def invokeService(params: Map[String, Any])(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ScenarioProcessingContextId,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    componentDefWithImpl.implementationInvoker
      .invokeMethod(
        params,
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(ec, collector, metaData, nodeId, contextId, componentUseCase)
      )
      .asInstanceOf[Future[AnyRef]]
  }

}

private[nodecompilation] class JavaMethodBasedServiceInvoker(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefWithImpl: ComponentDefinitionWithImplementation
) extends ServiceInvoker
    with LazyLogging {

  override def invokeService(params: Map[String, Any])(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ScenarioProcessingContextId,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    val result = componentDefWithImpl.implementationInvoker.invokeMethod(
      params,
      outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
      additional = Seq(ec, collector, metaData, nodeId, contextId, componentUseCase)
    )
    FutureConverters.toScala(result.asInstanceOf[CompletionStage[AnyRef]])
  }

}

object MethodBasedServiceInvoker {

  def apply(
      metaData: MetaData,
      nodeId: NodeId,
      outputVariableNameOpt: Option[OutputVar],
      componentDefWithImpl: MethodBasedComponentDefinitionWithImplementation
  ): ServiceInvoker = {
    val detectedRuntimeClass = componentDefWithImpl.runtimeClass
    if (classOf[Future[_]].isAssignableFrom(detectedRuntimeClass))
      new MethodBasedServiceInvoker(metaData, nodeId, outputVariableNameOpt, componentDefWithImpl)
    else if (classOf[java.util.concurrent.CompletionStage[_]].isAssignableFrom(detectedRuntimeClass))
      new JavaMethodBasedServiceInvoker(metaData, nodeId, outputVariableNameOpt, componentDefWithImpl)
    else
      throw new IllegalArgumentException(
        "Illegal detected runtime class of extracted method: " +
          detectedRuntimeClass + ". Should be Future or CompletionStage"
      )
  }

}
