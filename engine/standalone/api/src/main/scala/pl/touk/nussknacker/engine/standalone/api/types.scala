package pl.touk.nussknacker.engine.standalone.api

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, JoinReference}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.api.types.InterpreterOutputType

import scala.concurrent.{ExecutionContext, Future}

object types {

  type SuccessfulResultType = List[InterpretationResult]

  type GenericResultType[T] = Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], T]

  type GenericListResultType[T] = GenericResultType[List[T]]

  type InternalInterpretationResultType = GenericListResultType[PartResultType]

  type InterpretationResultType = GenericListResultType[InterpretationResult]

  type InterpreterOutputType = Future[InterpretationResultType]

  type InternalInterpreterOutputType = Future[InternalInterpretationResultType]

  type InterpreterType = (List[Context], ExecutionContext) => InternalInterpreterOutputType


  type JoinType = (Map[String, List[Context]], ExecutionContext) => InterpreterOutputType

  sealed trait PartResultType
  case class EndResult(result: InterpretationResult) extends PartResultType
  case class JoinResult(reference: JoinReference, context: Context) extends PartResultType

}
