package pl.touk.nussknacker.engine.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{MetaData, Service}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{NodeContext, ServiceInvocationCollector}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, Parameter}

import scala.concurrent.{ExecutionContext, Future}

trait ServiceInvoker {
  def invoke(params: Map[String, Any], nodeContext: NodeContext)
            (implicit ec: ExecutionContext, metaData: MetaData): Future[Any]
}

private[definition] class ServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef) extends ServiceInvoker with LazyLogging {

  override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                     (implicit ec: ExecutionContext, metaData: MetaData): Future[Any] = {
    //FIXME: why is this instanceOf necessary???
      objectWithMethodDef.invokeMethod((params.get _)
        .andThen(_.map(_.asInstanceOf[AnyRef])), Seq(ec,
        ServiceInvocationCollector(nodeContext), metaData)).asInstanceOf[Future[Any]]
  }
}

object ServiceInvoker {

  def apply(objectWithMethodDef: ObjectWithMethodDef): ServiceInvoker =
    new ServiceInvokerImpl(objectWithMethodDef)

}

object ServiceDefinitionExtractor extends DefinitionExtractor[Service] {

  override protected val returnType = classOf[Future[_]]
  override protected val additionalParameters = Set[Class[_]](classOf[ExecutionContext],
    classOf[ServiceInvocationCollector], classOf[MetaData])

}