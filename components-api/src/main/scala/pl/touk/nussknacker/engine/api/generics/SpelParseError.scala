package pl.touk.nussknacker.engine.api.generics

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait SpelParseError {
  def message: String
}

abstract class ArgumentTypeError extends SpelParseError {
  def found: List[TypingResult]

  def functionName: String

  def expectedString: String

  protected def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")

  override def message: String =
    s"Mismatch parameter types. Found: $functionName(${typesToString(found)}). Required: $functionName($expectedString)"
}

final class NoVarArgumentTypeError(expectedInner: List[TypingResult],
                                   foundInner: List[TypingResult],
                                   functionNameInner: String) extends ArgumentTypeError {
  override def expectedString: String = typesToString(expectedInner)

  override def found: List[TypingResult] = foundInner

  override def functionName: String = functionNameInner
}

final class VarArgumentTypeError(expectedInner: List[TypingResult],
                                 expectedVarArgInner: TypingResult,
                                 foundInner: List[TypingResult],
                                 functionNameInner: String) extends ArgumentTypeError {
  override def found: List[TypingResult] = foundInner

  override def functionName: String = functionNameInner

  override def expectedString: String = typesToString(expectedInner :+ expectedVarArgInner) + "..."
}

class GenericFunctionError(messageInner: String) extends SpelParseError {
  override def message: String = messageInner
}


