package pl.touk.nussknacker.engine.definition

import java.util.concurrent.{CompletionStage, Executor}

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{NodeContext, ServiceInvocationCollector, TestServiceInvocationCollector}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MetaData, Service}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.UnionDefinitionExtractor

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

trait ServiceInvoker {

  def invoke(params: Map[String, Any], nodeContext: NodeContext)
            (implicit ec: ExecutionContext, metaData: MetaData): Future[Any]

}

private[definition] class ServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef, collector: Option[ServiceInvocationCollector] = None)
  extends ServiceInvoker with LazyLogging {

  override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                     (implicit ec: ExecutionContext, metaData: MetaData): Future[Any] = {
    objectWithMethodDef.invokeMethod(
      paramFun = (params.get _)
        .andThen(_.map(_.asInstanceOf[AnyRef])),
      additional = Seq(ec, collector.getOrElse(TestServiceInvocationCollector(nodeContext)), metaData)
    ).asInstanceOf[Future[Any]]
  }

}

private[definition] class JavaServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef, collector: Option[ServiceInvocationCollector] = None)
  extends ServiceInvoker with LazyLogging {

  override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                     (implicit ec: ExecutionContext, metaData: MetaData): Future[Any] = {
    val result = objectWithMethodDef.invokeMethod((params.get _)
      .andThen(_.map(_.asInstanceOf[AnyRef])), Seq(prepareExecutor(ec), collector.getOrElse(TestServiceInvocationCollector(nodeContext)), metaData))
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
    val detectedReturnType = (objectWithMethodDef.methodDef.realReturnType match {
      case Typed(set) =>
        //FIXME: this headOption assumes that there will be only one class here
        //in the future we should get rid of Set in Typed...
        set.headOption.map(_.klass)
      case _ => None
    }).getOrElse(classOf[Any])
    if (classOf[Future[_]].isAssignableFrom(detectedReturnType))
      new ServiceInvokerImpl(objectWithMethodDef, collector)
    else if (classOf[java.util.concurrent.CompletionStage[_]].isAssignableFrom(detectedReturnType))
      new JavaServiceInvokerImpl(objectWithMethodDef, collector)
    else
      throw new IllegalArgumentException("Illegal return type of extracted method: " +
        detectedReturnType + ". Should be Future or CompletionStage")
  }


  private object ServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[Future[_]])
    override protected val additionalParameters = Set[Class[_]](classOf[ExecutionContext],
      classOf[ServiceInvocationCollector], classOf[MetaData])

  }

  private object JavaServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

    override protected val expectedReturnType: Option[Class[_]] = Some(classOf[java.util.concurrent.CompletionStage[_]])
    override protected val additionalParameters = Set[Class[_]](classOf[Executor],
      classOf[ServiceInvocationCollector], classOf[MetaData])

  }

}

