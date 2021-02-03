package pl.touk.nussknacker.engine.definition

import java.util.concurrent.{CompletionStage, Executor}

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{NodeContext, ServiceInvocationCollector, TestServiceInvocationCollector}
import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult
import pl.touk.nussknacker.engine.api.{MetaData, Service, ContextId}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.UnionDefinitionExtractor

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

trait ServiceInvoker {

  //TODO: we should allow to use services returning IO and synchronous services
  def invoke(params: Map[String, Any], nodeContext: NodeContext)
            (implicit ec: ExecutionContext, metaData: MetaData): Future[Any]

}

private[definition] class ServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef, collector: Option[ServiceInvocationCollector] = None)
  extends ServiceInvoker with LazyLogging {

  override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                     (implicit ec: ExecutionContext, metaData: MetaData): Future[Any] = {
    objectWithMethodDef.invokeMethod(params,
      outputVariableNameOpt = nodeContext.outputVariableNameOpt,
      additional = Seq(ec, collector.getOrElse(TestServiceInvocationCollector(nodeContext)), metaData, NodeId(nodeContext.nodeId), ContextId(nodeContext.contextId))
    ).asInstanceOf[Future[Any]]
  }

}

private[definition] class JavaServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef, collector: Option[ServiceInvocationCollector] = None)
  extends ServiceInvoker with LazyLogging {

  override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                     (implicit ec: ExecutionContext, metaData: MetaData): Future[Any] = {
    val result = objectWithMethodDef.invokeMethod(
      params,
      outputVariableNameOpt = None,
      additional = Seq(prepareExecutor(ec), collector.getOrElse(TestServiceInvocationCollector(nodeContext)), metaData, NodeId(nodeContext.nodeId), ContextId(nodeContext.contextId)))
    FutureConverters.toScala(result.asInstanceOf[CompletionStage[_]])
  }

  private def prepareExecutor(ec: ExecutionContext) =
    new Executor {
      override def execute(command: Runnable): Unit = {
        ec.execute(command)
      }
    }

}

object ServiceInvoker {

  final val Extractor: MethodDefinitionExtractor[Service] = new UnionDefinitionExtractor(
    ServiceDefinitionExtractor ::
      JavaServiceDefinitionExtractor ::
      Nil
  )

  def apply(objectWithMethodDef: ObjectWithMethodDef, collector: Option[ServiceInvocationCollector] = None): ServiceInvoker = {
    val detectedRuntimeClass = objectWithMethodDef.runtimeClass
    if (classOf[Future[_]].isAssignableFrom(detectedRuntimeClass))
      new ServiceInvokerImpl(objectWithMethodDef, collector)
    else if (classOf[java.util.concurrent.CompletionStage[_]].isAssignableFrom(detectedRuntimeClass))
      new JavaServiceInvokerImpl(objectWithMethodDef, collector)
    else
      throw new IllegalArgumentException("Illegal detected runtime class of extracted method: " +
        detectedRuntimeClass + ". Should be Future or CompletionStage")
  }


  private object ServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[Future[_]])
    override protected val additionalDependencies = Set[Class[_]](classOf[ExecutionContext],
      classOf[ServiceInvocationCollector], classOf[MetaData], classOf[NodeId], classOf[ContextId])

  }

  private object JavaServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[java.util.concurrent.CompletionStage[_]])
    override protected val additionalDependencies = Set[Class[_]](classOf[Executor],
      classOf[ServiceInvocationCollector], classOf[MetaData], classOf[NodeId], classOf[ContextId])

  }

}

