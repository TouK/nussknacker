package pl.touk.nussknacker.engine.api.generics

import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
 * Class representing additional information about methods type.
 *
 * <p>
 * Deriving classes must be declared as non-anonymous class or case class,
 * they have to be static or declared at top level, and they must have
 * parameterless constructor. More precisely, they must be instantiable
 * using:
 * <pre>typeFunctionClass.getDeclaredConstructor().newInstance()</pre>
 */
abstract class TypingFunction {

  /**
   * List of possible combinations of parameters and result types that
   * this function can accept. Must be at least as specific as types that
   * can be derived from associated method and less specific than
   * computeResultType.
   * Defaults to type of associated method if empty list is provided.
   */
  def signatures: Option[NonEmptyList[MethodTypeInfo]] = None

  // TODO: Add instance invocation target if we decide that this feature is useful not only for global (static) variables
  //       but also for other classes. Our backend is already ready for this - see FunctionalMethodDefinition
  def computeResultType(arguments: List[TypingResult]): ValidatedNel[GenericFunctionTypingError, TypingResult]
}
