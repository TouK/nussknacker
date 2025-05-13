package pl.touk.nussknacker.engine.compile

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CannotCreateObjectError,
  CustomNodeError,
  MissingParameters
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, MissingOutputVariableException}

import scala.util.control.NonFatal

object NodeValidationExceptionHandler extends LazyLogging {

  def handleExceptions[T](
      f: => T
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    handleExceptionsInValidation(Valid(f))
  }

  def handleExceptionsInValidation[T](
      f: => ValidatedNel[ProcessCompilationError, T]
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    try {
      f
    } catch {
      case MissingOutputVariableException =>
        Validated.invalidNel(MissingParameters(Set(ParameterName("OutputVariable")), nodeId.id))
      case exc: CustomNodeValidationException =>
        Validated.invalidNel(CustomNodeError(exc.message, exc.paramName))
      case NonFatal(e) =>
        logger.error(
          s"Exception during validation handling of node '${nodeId.id}' in scenario ${metaData.name.value}",
          e
        )
        Validated.invalidNel(CannotCreateObjectError(e.getMessage, nodeId.id))
    }
  }

}
