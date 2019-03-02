package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
  * Trait to be mixed in CustomStreamTransformer or SourceFactory which can return various types
  * depending on input (as in dependent types in CS).
  * @see GenericTypedJsonSourceFactory or PreviousValueTransformer
  */
trait ReturningType {

  def returnType: TypingResult

}

/**
  * Trait to be mixed in Service Source which can return various types
  * depending on input (as in dependent types in CS).
  * @see ReturningDependentTypeService in tests
  *
  * This trait is more complex, as Service is not factory but is invoked directly
  *
  * TODO: rethink design - can it be easier to use and more typesafe?
  */
trait ServiceReturningType {

  /**
    * Map of parameters. Type derived from passed expression is always given, also
    * value (if expression is more or less constant) can be given, but of course it's not always possible
    * at compile time (e.g. #input.field1)
    */
  def returnType(parameters: Map[String, (TypingResult, Option[Any])]): TypingResult

}
