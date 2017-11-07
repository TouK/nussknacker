package pl.touk.nussknacker.engine.standalone.api

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

import scala.concurrent.{ExecutionContext, Future}

object types {

  type SuccessfulResultType = List[InterpretationResult]

  type GenericResultType[T] = Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], T]

  type GenericListResultType[T] = GenericResultType[List[T]]

  type InterpretationResultType = GenericListResultType[InterpretationResult]

  type InterpreterOutputType = Future[InterpretationResultType]

  type InterpreterType = (Context, ExecutionContext) => InterpreterOutputType
}
