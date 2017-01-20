package pl.touk.esp.engine.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.api.test.InvocationCollectors.{NodeContext, ServiceInvocationCollector}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, Parameter}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait ServiceInvoker {
  def invoke(params: Map[String, Any], nodeContext: NodeContext)
            (implicit ec: ExecutionContext): Future[Any]
}

private[definition] class ServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef) extends ServiceInvoker with LazyLogging {

  override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                     (implicit ec: ExecutionContext): Future[Any] = {
    def prepareValue(p: Parameter): Any =
      params.getOrElse(
        p.name,
        throw new IllegalArgumentException(s"Missing parameter with name: ${p.name}")
      )
    val values = objectWithMethodDef.orderedParameters.prepareValues(prepareValue, Seq(ec, ServiceInvocationCollector(nodeContext)))
    try {
      objectWithMethodDef.invokeMethod(values).asInstanceOf[Future[Any]]
    } catch {
      case ex: IllegalArgumentException =>
        logger.warn(s"Failed to invoke method: ${objectWithMethodDef.methodDef.method}, with params: $values", ex)
        throw ex
      case NonFatal(ex) =>
        throw ex
    }
  }

}

object ServiceInvoker {

  def apply(objectWithMethodDef: ObjectWithMethodDef): ServiceInvoker =
    new ServiceInvokerImpl(objectWithMethodDef)

}

object ServiceDefinitionExtractor extends DefinitionExtractor[Service] {

  override protected val returnType = classOf[Future[_]]
  override protected val additionalParameters = Set[Class[_]](classOf[ExecutionContext], classOf[ServiceInvocationCollector])

}