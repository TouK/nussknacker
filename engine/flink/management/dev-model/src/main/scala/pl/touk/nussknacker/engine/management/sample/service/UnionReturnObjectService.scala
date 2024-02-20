package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ContextId, MetaData, Params}
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import scala.concurrent.{ExecutionContext, Future}

object UnionReturnObjectService extends EagerServiceWithStaticParametersAndReturnType {

  override def invoke(params: Params)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ContextId,
      metaData: MetaData,
      componentUseCase: ComponentUseCase
  ): Future[Any] =
    Future.successful(Map("foo" -> 1))

  override def parameters: List[Parameter] = List.empty

  override def returnType: typing.TypingResult =
    Typed(Typed.record(Map("foo" -> Typed[Int])), Typed.record(Map("bar" -> Typed[Int])))

}
