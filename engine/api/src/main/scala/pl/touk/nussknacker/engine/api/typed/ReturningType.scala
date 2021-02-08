package pl.touk.nussknacker.engine.api.typed

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

/**
  * Trait to be mixed in Service Source which can return various types
  * depending on input (as in dependent types in CS).
  * @see ReturningDependentTypeService in tests
  *
  * You can implement custom validation at parameter or service level by throwing
  * CustomParameterValidationException or CustomServiceValidationException respectively
  *
  * This trait is more complex, as Service is not factory but is invoked directly
  */
// TODO: Replace with EagerService with LazyParameter's and ContextTransformation API
@deprecated("Use EagerService instead", since = "0.4.0")
trait ServiceReturningType {

  /**
    * Map of parameters. Type derived from passed expression is always given, also
    * value (if expression is more or less constant) can be given, but of course it's not always possible
    * at compile time (e.g. #input.field1)
    */
  def returnType(parameters: Map[String, (TypingResult, Option[Any])]): TypingResult

}

case object MissingOutputVariableException extends Exception("Missing output variable name")

case class CustomNodeValidationException(message: String, paramName: Option[String], parent: Throwable)
  extends RuntimeException(message, parent)

object CustomNodeValidationException {
  
  def apply(message: String, paramName: Option[String]): CustomNodeValidationException =
    CustomNodeValidationException(message, paramName, null)

  def apply(exc: Exception, paramName: Option[String]): CustomNodeValidationException =
    CustomNodeValidationException(exc.getMessage, paramName, exc)
}
