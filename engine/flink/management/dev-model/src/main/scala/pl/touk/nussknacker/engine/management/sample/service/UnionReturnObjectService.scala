package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{Context, ContextId, MetaData}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import scala.concurrent.{ExecutionContext, Future}

object UnionReturnObjectService extends EagerServiceWithStaticParametersAndReturnType {

  override def invoke(evaluateParams: Context => (Context, Map[String, Any]))(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      context: Context,
      metaData: MetaData,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] =
    Future.successful(Map("foo" -> 1))

  override def parameters: List[Parameter] = List.empty

  override def returnType: typing.TypingResult =
    Typed(Typed.record(Map("foo" -> Typed[Int])), Typed.record(Map("bar" -> Typed[Int])))

}
