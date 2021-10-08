package pl.touk.nussknacker.engine.definition

import java.util.concurrent.{CompletionStage, Executor}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.{ContextId, EagerService, MetaData, Service, ServiceInvoker}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.UnionDefinitionExtractor

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

private[definition] class ServiceInvokerImpl(metaData: MetaData,
                                             nodeId: NodeId,
                                             outputVariableNameOpt: Option[OutputVar],
                                             objectWithMethodDef: ObjectWithMethodDef)
  extends ServiceInvoker with LazyLogging {


  override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                       collector: ServiceInvocationCollector,
                                                       contextId: ContextId,
                                                       runMode: RunMode): Future[AnyRef] = {
    objectWithMethodDef.invokeMethod(params,
      outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
      additional = Seq(ec, collector, metaData, nodeId, contextId, runMode)
    ).asInstanceOf[Future[AnyRef]]
  }

}

private[definition] class JavaServiceInvokerImpl(metaData: MetaData,
                                             nodeId: NodeId,
                                             outputVariableNameOpt: Option[OutputVar],
                                             objectWithMethodDef: ObjectWithMethodDef)
  extends ServiceInvoker with LazyLogging {


  override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                       collector: ServiceInvocationCollector,
                                                       contextId: ContextId,
                                                       runMode: RunMode): Future[AnyRef] = {
    val result = objectWithMethodDef.invokeMethod(params,
      outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
      additional = Seq(ec, collector, metaData, nodeId, contextId, runMode)
    )
    FutureConverters.toScala(result.asInstanceOf[CompletionStage[AnyRef]])
  }

}

object DefaultServiceInvoker {

  final val Extractor: MethodDefinitionExtractor[Service] = new UnionDefinitionExtractor[Service](
    ServiceDefinitionExtractor ::
      JavaServiceDefinitionExtractor ::
      EagerServiceDefinitionExtractor ::
      Nil
  )

  def apply(metaData: MetaData,
            nodeId: NodeId,
            outputVariableNameOpt: Option[OutputVar],
            objectWithMethodDef: ObjectWithMethodDef): ServiceInvoker = {
    val detectedRuntimeClass = objectWithMethodDef.runtimeClass
    if (classOf[Future[_]].isAssignableFrom(detectedRuntimeClass))
      new ServiceInvokerImpl(metaData, nodeId, outputVariableNameOpt, objectWithMethodDef)
    else if (classOf[java.util.concurrent.CompletionStage[_]].isAssignableFrom(detectedRuntimeClass))
      new JavaServiceInvokerImpl(metaData, nodeId, outputVariableNameOpt, objectWithMethodDef)
    else
      throw new IllegalArgumentException("Illegal detected runtime class of extracted method: " +
        detectedRuntimeClass + ". Should be Future or CompletionStage")
  }


  private object ServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[Future[_]])
    override protected val additionalDependencies = Set[Class[_]](classOf[ExecutionContext],
      classOf[ServiceInvocationCollector], classOf[MetaData], classOf[NodeId], classOf[ContextId], classOf[RunMode])
    override def acceptCustomTransformation: Boolean = false
  }

  private object JavaServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[java.util.concurrent.CompletionStage[_]])
    override protected val additionalDependencies = Set[Class[_]](classOf[Executor],
      classOf[ServiceInvocationCollector], classOf[MetaData], classOf[NodeId], classOf[ContextId], classOf[RunMode])
    override def acceptCustomTransformation: Boolean = false
  }

  private object EagerServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[ServiceInvoker])
    override protected val additionalDependencies = Set[Class[_]](classOf[ExecutionContext],
      classOf[ServiceInvocationCollector], classOf[MetaData], classOf[NodeId], classOf[ContextId], classOf[RunMode])

  }

}

