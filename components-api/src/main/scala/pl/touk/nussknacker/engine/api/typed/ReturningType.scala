package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
  * Trait to be mixed in CustomStreamTransformer or SourceFactory which can return various types
  * depending on input (as in dependent types in CS).
  * Deprecated: use ContextTransformation
  * @see GenericTypedJsonSourceFactory or PreviousValueTransformer
  */
// TODO: remove after full switch to ContextTransformation API
trait ReturningType {

  def returnType: TypingResult

}

case object MissingOutputVariableException extends Exception("Missing output variable name")

case class CustomNodeValidationException(message: String, paramName: Option[ParameterName], parent: Throwable)
    extends RuntimeException(message, parent)

object CustomNodeValidationException {

  def apply(message: String, paramName: Option[ParameterName]): CustomNodeValidationException =
    CustomNodeValidationException(message, paramName, null)

  def apply(exc: Exception, paramName: Option[ParameterName]): CustomNodeValidationException =
    CustomNodeValidationException(exc.getMessage, paramName, exc)
}
