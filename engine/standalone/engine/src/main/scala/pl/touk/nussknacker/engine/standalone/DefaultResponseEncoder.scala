package pl.touk.nussknacker.engine.standalone

import cats.Traverse.ops._
import cats.instances.list._
import cats.instances.either._
import cats.data.NonEmptyList
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.api.ResponseEncoder
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.util.control.NonFatal

object DefaultResponseEncoder extends ResponseEncoder[Any] {

  val bestEffortEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  override def toJsonResponse(input: Any, result: List[Any]): GenericResultType[Json] =
    result.map(toJsonOrError).sequence.right.map(Encoder[List[Json]].apply)

  private def toJsonOrError(value: Any): GenericResultType[Json] =
    try {
      Right(bestEffortEncoder.encode(value))
    } catch {
      case NonFatal(e) => Left(NonEmptyList.of(EspExceptionInfo(None, e, Context(""))))
    }

}
