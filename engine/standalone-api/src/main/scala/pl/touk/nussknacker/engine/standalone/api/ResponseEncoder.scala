package pl.touk.nussknacker.engine.standalone.api

import java.util

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
