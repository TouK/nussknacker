package pl.touk.nussknacker.engine.standalone.api

import argonaut._
import Argonaut._
import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.{Context, Displayable}
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType


trait ResponseEncoder {

  def toJsonResponse(input: Any, result: List[Any]): GenericResultType[Json]

}

object DefaultResponseEncoder extends ResponseEncoder {

  override def toJsonResponse(input: Any, result: List[Any]): GenericResultType[Json] =
    result.map(toJsonOrError).sequenceU.right.map(_.asJson)

  private def toJsonOrError(value: Any): GenericResultType[Json] = value match {
    case a: Displayable => Right(a.display)
    case a: String => Right(Json.jString(a))
    case a => Left(NonEmptyList.of(EspExceptionInfo(None, new IllegalArgumentException(s"Invalid result type: ${a.getClass}"),
      Context(""))))
  }

}
