package pl.touk.nussknacker.engine.standalone

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes._
import pl.touk.nussknacker.engine.standalone.api.ResponseEncoder
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.util.control.NonFatal

object DefaultResponseEncoder extends ResponseEncoder[Any] {

  private val bestEffortEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  override def toJsonResponse(input: Any, result: List[Any]): GenericResultType[Json] = {
    try {
      Right(bestEffortEncoder.encode(result))
    } catch {
      case NonFatal(e) => Left(EspExceptionInfo(None, e, Context("")))
    }
  }

}
