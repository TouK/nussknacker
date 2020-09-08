package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.api.definition.{Parameter, ServiceWithExplicitMethod}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}

import scala.concurrent.{ExecutionContext, Future}

case object UnionReturnObjectService extends ServiceWithExplicitMethod {

  override def invokeService(params: List[AnyRef])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, metaData: MetaData, contextId: ContextId): Future[AnyRef] =
    Future.successful(Map("foo" -> 1))

  override def parameterDefinition: List[Parameter] = List.empty

  override def returnType: typing.TypingResult = Typed(
    TypedObjectTypingResult(Map("foo" -> Typed[Int])),
    TypedObjectTypingResult(Map("bar" -> Typed[Int])))

}
