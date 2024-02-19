package pl.touk.nussknacker.engine.compile

import cats.data.Validated.Valid
import cats.data.{Validated, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CannotCreateObjectError,
  CustomNodeError,
  MissingParameters
}
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, MissingOutputVariableException}

import scala.util.control.NonFatal

object NodeValidationExceptionHandler extends LazyLogging {

  def handleExceptions[T](f: => T)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, T] = {
    handleExceptionsInValidation(Valid(f))
  }

  def handleExceptionsInValidation[T](
      f: => ValidatedNel[ProcessCompilationError, T]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, T] = {
    try {
      f
    } catch {
      case MissingOutputVariableException =>
        Validated.invalidNel(MissingParameters(Set("OutputVariable"), nodeId.id))
      case exc: CustomNodeValidationException =>
        Validated.invalidNel(CustomNodeError(exc.message, exc.paramName))
      case NonFatal(e) =>
        // TODO: better message?
        Validated.invalidNel(CannotCreateObjectError(e.getMessage, nodeId.id))
    }
  }

}
