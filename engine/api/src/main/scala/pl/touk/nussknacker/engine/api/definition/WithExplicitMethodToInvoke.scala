package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}

import scala.concurrent.{ExecutionContext, Future}

trait WithExplicitMethodToInvoke {

  def parameterDefinition: List[Parameter]

  def returnType: TypingResult

  def additionalParameters: List[Class[_]]

  def invoke(params: List[AnyRef]): AnyRef

}


trait ServiceWithExplicitMethod extends Service with WithExplicitMethodToInvoke {

  override final def additionalParameters: List[Class[_]] = List(classOf[ExecutionContext], classOf[ServiceInvocationCollector], classOf[MetaData])

  override final def returnType = Typed(Set(TypedClass(classOf[Future[_]], List(serviceReturnType))))

  override def invoke(params: List[AnyRef]): AnyRef = {
    val normalParams = params.dropRight(3)
    val implicitParams = params.takeRight(3)
    invokeService(normalParams)(implicitParams(0).asInstanceOf[ExecutionContext],
                                implicitParams(1).asInstanceOf[ServiceInvocationCollector],
                                implicitParams(2).asInstanceOf[MetaData])
  }

  def serviceReturnType: TypingResult

  def invokeService(params: List[AnyRef])(implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, metaData: MetaData): Future[AnyRef]


}