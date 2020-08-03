package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, CustomNodeError, MissingParameters, NodeId}
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, MissingOutputVariableException}

import scala.util.control.NonFatal

object NodeValidationExceptionHandler {

  def handleExceptions[T](f: => T)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, T] = {
    handleExceptionsInValidation(Valid(f))
  }

  def handleExceptionsInValidation[T](f: => ValidatedNel[ProcessCompilationError, T])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, T] = {
    try {
      f
    } catch {
      case MissingOutputVariableException =>
        Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"), nodeId.id)))
      case exc: CustomNodeValidationException =>
        Invalid(NonEmptyList.of(CustomNodeError(exc.message, exc.paramName)))
      case NonFatal(e) =>
        //TODO: better message?
        Invalid(NonEmptyList.of(CannotCreateObjectError(e.getMessage, nodeId.id)))
    }
  }

}
