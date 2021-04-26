package pl.touk.nussknacker.engine.standalone.api

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, JoinReference}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.api.types.InterpreterOutputType

import scala.concurrent.{ExecutionContext, Future}

object types {

  type SuccessfulResultType = List[InterpretationResult]

  type ErrorType = NonEmptyList[EspExceptionInfo[_ <: Throwable]]

  type GenericResultType[T] = Either[ErrorType, T]

  type GenericListResultType[T] = GenericResultType[List[T]]

  type InterpretationResultType = GenericListResultType[InterpretationResult]

  type InterpreterOutputType = Future[InterpretationResultType]

  type InternalInterpreterOutputType = Future[GenericListResultType[PartResultType]]

  type InterpreterType = (List[Context], ExecutionContext) => InternalInterpreterOutputType
  
  sealed trait PartResultType
  case class EndResult(result: InterpretationResult) extends PartResultType
  case class JoinResult(reference: JoinReference, context: Context) extends PartResultType

}
