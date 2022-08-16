package pl.touk.nussknacker.engine.api.generics

import cats.data.NonEmptyList

sealed trait GenericFunctionTypingError

object GenericFunctionTypingError {
  case object ArgumentTypeError extends GenericFunctionTypingError

  case class ArgumentTypeErrorWithSignatures(signatures: NonEmptyList[Signature]) extends GenericFunctionTypingError

  trait CustomError extends GenericFunctionTypingError {
    def message: String
  }

  case class OtherError(message: String) extends CustomError
}