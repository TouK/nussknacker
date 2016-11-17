package pl.touk.esp.engine.definition

import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, Parameter}

import scala.concurrent.{ExecutionContext, Future}

trait ServiceInvoker {
  def invoke(params: Map[String, Any])
            (implicit ec: ExecutionContext): Future[Any]
}

private[definition] class ServiceInvokerImpl(objectWithMethodDef: ObjectWithMethodDef) extends ServiceInvoker {

  override def invoke(params: Map[String, Any])
                     (implicit ec: ExecutionContext): Future[Any] = {
    def prepareValue(p: Parameter): Any =
      params.getOrElse(
        p.name,
        throw new IllegalArgumentException(s"Missing parameter with name: ${p.name}")
      )
    val values = objectWithMethodDef.orderedParameters.prepareValues(prepareValue, Seq(ec))
    objectWithMethodDef.method.invoke(objectWithMethodDef.obj, values: _*).asInstanceOf[Future[Any]]
  }

}

object ServiceInvoker {

  //TODO: to w sumie metoda tylko do testow...
  def apply(service: Service) =
    new ServiceInvokerImpl(ObjectWithMethodDef(service, ServiceDefinitionExtractor.extractMethodDefinition(service),
      ServiceDefinitionExtractor.extract(service, List())))

  def apply(objectWithMethodDef: ObjectWithMethodDef): ServiceInvoker =
    new ServiceInvokerImpl(objectWithMethodDef)

}

object ServiceDefinitionExtractor extends DefinitionExtractor[Service] {

  override protected val returnType = classOf[Future[_]]
  override protected val additionalParameters = Set[Class[_]](classOf[ExecutionContext])

}