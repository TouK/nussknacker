package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{MetaData, ServiceLogic}
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import scala.concurrent.{ExecutionContext, Future}

case object UnionReturnObjectService extends EagerServiceWithStaticParametersAndReturnType {

  override val parameters: List[Parameter] = List.empty

  override val returnType: typing.TypingResult =
    Typed(TypedObjectTypingResult(Map("foo" -> Typed[Int])), TypedObjectTypingResult(Map("bar" -> Typed[Int])))

  override def runServiceLogic(
      paramsEvaluator: ServiceLogic.ParamsEvaluator
  )(
      implicit runContext: ServiceLogic.RunContext,
      metaData: MetaData,
      executionContext: ExecutionContext
  ): Future[Any] = {
    Future.successful(Map("foo" -> 1))
  }

}
