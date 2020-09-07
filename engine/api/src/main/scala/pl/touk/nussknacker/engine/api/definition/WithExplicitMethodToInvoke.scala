package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{ContextId, MetaData, Service}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Sometimes we don't want to use annotations and reflection to detect parameters and return type.
  * Good example is generating enrichers dynamically from e.g. OpenAPI
  * This trait is generic, can be used for generating sources, enrichers and so on.
  * Convenience traits e.g. ServiceWithExplicitMethod should be used for generating concrete examples
  * as they handle implicit arguments better
  *
  */
trait WithExplicitMethodToInvoke {

  def parameterDefinition: List[Parameter]

  def returnType: TypingResult

  def runtimeClass: Class[_]

  def additionalDependencies: List[Class[_]]

  def invoke(params: List[AnyRef]): AnyRef

}


trait ServiceWithExplicitMethod extends Service with WithExplicitMethodToInvoke {

  override final def additionalDependencies: List[Class[_]] = List(classOf[ExecutionContext], classOf[ServiceInvocationCollector], classOf[MetaData], classOf[ContextId])

  override final def runtimeClass: Class[_] = classOf[Future[_]]

  override def invoke(params: List[AnyRef]): AnyRef = {
    val normalParams = params.dropRight(4)
    val implicitParams = params.takeRight(4)
    invokeService(normalParams)(implicitParams(0).asInstanceOf[ExecutionContext],
                                implicitParams(1).asInstanceOf[ServiceInvocationCollector],
                                implicitParams(2).asInstanceOf[MetaData],
                                implicitParams(3).asInstanceOf[ContextId])
  }

  def invokeService(params: List[AnyRef])(implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, metaData: MetaData, contextId: ContextId): Future[AnyRef]

}