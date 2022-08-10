package pl.touk.nussknacker.engine.api.generics

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
 * Base for creating custom classes that extend methods signature.
 *
 * <p>
 * Deriving classes must be declared as non-anonymous class or case class,
 * they have to be static or declared at top level, and they must have
 * parameterless constructor. More precisely, they must be instantiable
 * using:
 * <pre>typeFunctionClass.getDeclaredConstructor().newInstance()</pre>
 *
 * <p>
 * Constructor of deriving class will be called every time appropriate
 * method is validated, so it should not do any unnecessary computations.
 */
abstract class TypingFunction {
  /**
   * Approximation of types of parameters that can be accepted
   * by method. Used for displaying information about method on FE
   * and generating error messages. Defaults to types that can be
   * extracted from methods signature if it is not specified.
   */
  def staticParameters: Option[ParameterList] = None

  /**
   * Approximation of return type of method. Used for displaying
   * information about method on FE and for method suggestions.
   * Defaults to type extracted from methods signature if it is
   * not specified.
   */
  def staticResult: Option[TypingResult] = None

  def computeResultType(arguments: List[TypingResult]): ValidatedNel[GenericFunctionTypingError, TypingResult]
}
