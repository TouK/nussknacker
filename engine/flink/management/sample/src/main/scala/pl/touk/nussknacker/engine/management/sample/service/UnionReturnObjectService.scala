package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.util.service.ServiceWithStaticParametersAndReturnType

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

case object UnionReturnObjectService extends ServiceWithStaticParametersAndReturnType {

  override def invoke(params: Map[String, Any])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, contextId: ContextId, metaData: MetaData): Future[AnyRef] =
    Future.successful(Map("foo" -> 1))

  override def parameters: List[Parameter] = List.empty

  override def returnType: typing.TypingResult = Typed(
    TypedObjectTypingResult(ListMap("foo" -> Typed[Int])),
    TypedObjectTypingResult(ListMap("bar" -> Typed[Int])))

}
